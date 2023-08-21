package sip.reactor_netty;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.kurento.client.KurentoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import document_editor.netty_reactor.DocumentEditingServer;
import io.netty.buffer.Unpooled;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;
import sip.FullSipURI;
import sip.SipMessage;
import sip.SipMessageReader;
import sip.reactor_netty.request_handling.CreateConferenceReactiveSipRequestHandler;
import sip.reactor_netty.request_handling.DelegatingReactiveSipMessageHandler;
import sip.reactor_netty.request_handling.JoinConferenceReactiveSipRequestHandler;
import sip.reactor_netty.request_handling.ReactiveRegisterRequestHandler;
import sip.reactor_netty.service.InMemoryReactiveBindingStorage;
import sip.request_handling.InMemoryInviteDialogService;
import sip.request_handling.invite.KurentoMediaConferenceService;
import tcp.server.reader.MessageReader;
import util.Pair;

public class WebSocketCallingServerReactive {
	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentEditingServer.class);
	private static final String REGISTER = "REGISTER";
	private static final String INVITE = "INVITE";
	private static final String CONFERENCE_FACTORY = "conference-factory";

	public static void main(String[] args) {
		MessageReader<SipMessage> sipMessageReader = new SipMessageReader();

		var reactiveBindingStorage = new InMemoryReactiveBindingStorage();
		var kurentoClient = KurentoClient.create();
		var mediaConferenceService = new KurentoMediaConferenceService(kurentoClient);
		var dialogService = new InMemoryInviteDialogService();

		var delegatingReactiveSipMessageHandler = new DelegatingReactiveSipMessageHandler(
				Map.of(
						REGISTER, List.of(new ReactiveRegisterRequestHandler(reactiveBindingStorage)),
						INVITE, List.of(
								new CreateConferenceReactiveSipRequestHandler(
										addressOfRecord -> {
											var sipURI = (FullSipURI) addressOfRecord.sipURI();
											return sipURI.credentials().username().equals(CONFERENCE_FACTORY);
										},
										() -> "conference-" + UUID.randomUUID(),
										mediaConferenceService
								),
								new JoinConferenceReactiveSipRequestHandler(
										mediaConferenceService,
										null, // TODO:
										dialogService
								)
						)
				),
				Map.of()
		);


		DisposableServer server =
				HttpServer.create()
						.port(getPort())
						.accessLog(true)
						.noSSL()
						.protocol(HttpProtocol.HTTP11)
						.route(routes ->
								routes
										.get("/hello",
												(request, response) -> response.sendString(Mono.just("Hello World!")))
										.post("/echo",
												(request, response) -> response.send(request.receive().retain()))
										.get("/path/{param}",
												(request, response) -> response.sendString(Mono.just(request.param("param"))))
										.ws("/", (wsInbound, wsOutbound) -> {

											wsInbound.receiveCloseStatus()
													.subscribeOn(Schedulers.parallel())
													.subscribe(closeStatus ->
															LOGGER.info("Connection closed with code = {} reason = {}",
																	closeStatus.code(), closeStatus.reasonText()));

											return wsInbound
													.aggregateFrames()
													.receiveFrames()
													.mapNotNull(v -> sipMessageReader.read(new NettyBufferBytesSource(v.content()), e -> {
														// TODO: Create trace span for this...
													}))
													.map(Pair::first)
													.flatMap(sipMessage -> {
														LOGGER.info("New SIP message = {}", sipMessage);
														return wsOutbound.send(
																delegatingReactiveSipMessageHandler.handleMessage(
																				sipMessage,
																				objectsPublisher -> wsOutbound.send(objectsPublisher
																								.map(obj -> {
																									final int size = obj.getSize();
																									var byteBuffer = ByteBuffer.allocateDirect(size);
																									obj.serialize(byteBuffer);
																									return Unpooled.wrappedBuffer(byteBuffer);
																								}))
																		)
																		.map(obj -> {
																			var size = obj.getSize();
																			var byteBuffer = ByteBuffer.allocateDirect(size);
																			obj.serialize(byteBuffer);
																			return Unpooled.wrappedBuffer(byteBuffer);
																		}));
													})
													.subscribeOn(Schedulers.parallel());
										}))
						.bindNow();

		server.onDispose().block();
	}

	private static int getPort() {
		return Optional.ofNullable(System.getenv("PORT"))
				.map(Integer::parseInt)
				.orElse(8000);
	}
}
