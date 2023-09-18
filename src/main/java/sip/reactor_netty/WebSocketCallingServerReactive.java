package sip.reactor_netty;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.kurento.client.KurentoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import document_editor.netty_reactor.DocumentEditingServer;
import io.netty.buffer.Unpooled;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.WebsocketServerSpec;
import serialization.JacksonDeserializer;
import serialization.Serializer;
import sip.SipMessageReader;
import sip.reactor_netty.request_handling.ConfirmParticipantOffersSipReactiveResponseHandler;
import sip.reactor_netty.request_handling.CreateConferenceReactiveSipRequestHandler;
import sip.reactor_netty.request_handling.DelegatingReactiveSipMessageHandler;
import sip.reactor_netty.request_handling.JoinConferenceReactiveSipRequestHandler;
import sip.reactor_netty.request_handling.LeaveConferenceReactiveSipRequestHandler;
import sip.reactor_netty.request_handling.ReactiveRegisterRequestHandler;
import sip.reactor_netty.request_handling.SubscribeToConferenceUpdatesReactiveSipRequestHandler;
import sip.reactor_netty.request_handling.UnsubscribeFromConferenceUpdatesReactiveSipRequestHandler;
import sip.reactor_netty.service.impl.ConferenceEventDialogService;
import sip.reactor_netty.service.impl.InMemoryReactiveBindingStorage;
import sip.reactor_netty.service.impl.ReactiveConferenceSubscribersContext;
import sip.request_handling.InMemoryInviteDialogService;
import sip.request_handling.invite.KurentoMediaConferenceService;
import util.Pair;

public class WebSocketCallingServerReactive {
	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentEditingServer.class);

	private static final String REGISTER = "REGISTER";
	private static final String INVITE = "INVITE";
	private static final String SUBSCRIBE = "SUBSCRIBE";
	private static final String BYE = "BYE";
	private static final String CONFERENCE_FACTORY = "conference-factory";
	private static final String CONFERENCE_ID_PREFIX = "conference-";
	private static final Gson GSON = new Gson();
	public static final String NOTIFY = "NOTIFY";

	public static void main(String[] args) {
		var sipMessageReader = new SipMessageReader();

		var reactiveBindingStorage = new InMemoryReactiveBindingStorage();
		var kurentoClient = KurentoClient.create(System.getenv("KURENTO_URI"));
		var mediaConferenceService = new KurentoMediaConferenceService(kurentoClient);
		var dialogService = new InMemoryInviteDialogService();

		var serializer = (Serializer) obj -> GSON.toJson(obj).getBytes(StandardCharsets.UTF_8);
		var deserializer = new JacksonDeserializer(new ObjectMapper());

		var reactiveConferenceSubscribersContext = new ReactiveConferenceSubscribersContext(
				new ConferenceEventDialogService(),
				mediaConferenceService,
				serializer
		);
		var delegatingReactiveSipMessageHandler = new DelegatingReactiveSipMessageHandler(
				Map.of(
						REGISTER, List.of(new ReactiveRegisterRequestHandler(reactiveBindingStorage)),
						INVITE, List.of(
								new CreateConferenceReactiveSipRequestHandler(
										addressOfRecord -> {
											var sipURI = addressOfRecord.sipURI();
											return sipURI.credentials().username().equals(CONFERENCE_FACTORY);
										},
										() -> CONFERENCE_ID_PREFIX + UUID.randomUUID(),
										mediaConferenceService
								),
								new JoinConferenceReactiveSipRequestHandler(
										mediaConferenceService,
										reactiveConferenceSubscribersContext,
										dialogService
								)
						),
						SUBSCRIBE, List.of(
								new SubscribeToConferenceUpdatesReactiveSipRequestHandler(reactiveConferenceSubscribersContext),
								new UnsubscribeFromConferenceUpdatesReactiveSipRequestHandler(reactiveConferenceSubscribersContext)
						),
						BYE, List.of(
								new LeaveConferenceReactiveSipRequestHandler(
										mediaConferenceService,
										reactiveConferenceSubscribersContext
								))
				),
				Map.of(
						NOTIFY, List.of(
								new ConfirmParticipantOffersSipReactiveResponseHandler(mediaConferenceService, deserializer)
						)
				)
		);


		DisposableServer server =
				HttpServer.create()
						.port(getPort())
						.accessLog(true)
//						.wiretap(true)
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
														return wsOutbound.sendString(
																delegatingReactiveSipMessageHandler.handleMessage(
																				sipMessage,
																				objectsPublisher -> wsOutbound.send(objectsPublisher
																								.map(obj -> {
																									final int size = obj.getSize();
																									var byteBuffer = ByteBuffer.allocateDirect(size);
																									obj.serialize(byteBuffer);
																									byteBuffer.flip();
																									return Unpooled.wrappedBuffer(byteBuffer);
																								}))
																		)
																		.map(obj -> {
																			var size = obj.getSize();
																			var byteBuffer = ByteBuffer.allocateDirect(size);
																			obj.serialize(byteBuffer);
																			byteBuffer.flip();
																			byte[] bytes = new byte[byteBuffer.limit()];
																			byteBuffer.get(bytes);
																			return new String(bytes, StandardCharsets.UTF_8);

//																			return Unpooled.wrappedBuffer(byteBuffer);
																		}));
													})
													.subscribeOn(Schedulers.parallel());
										},
												WebsocketServerSpec.builder().protocols("sip").build()))
						.bindNow();

		server.onDispose().block();
	}

	private static int getPort() {
		return Optional.ofNullable(System.getenv("PORT"))
				.map(Integer::parseInt)
				.orElse(5068);
	}
}
