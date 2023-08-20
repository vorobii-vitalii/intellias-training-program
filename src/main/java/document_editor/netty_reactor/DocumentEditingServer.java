package document_editor.netty_reactor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.document.storage.RxDocumentStorageServiceGrpc;
import com.fasterxml.jackson.databind.ObjectMapper;

import document_editor.dto.ClientRequest;
import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import document_editor.netty_reactor.request_handling.ReactiveRequestHandler;
import document_editor.netty_reactor.request_handling.impl.ConnectReactiveRequestHandler;
import document_editor.netty_reactor.request_handling.impl.EditDocumentReactiveRequestHandler;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;
import serialization.JacksonDeserializer;
import serialization.Serializer;

public class DocumentEditingServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentEditingServer.class);
	public static final int PONG_INTERVAL = 5000;
	private static final Response PONG_RESPONSE = new Response(ResponseType.PONG, null);

	public static void main(String[] args) {
		var pongPublisher = new PeriodicalPublisherCreator<>(PONG_INTERVAL, () -> PONG_RESPONSE).create();

		var objectMapper = new ObjectMapper(new MessagePackFactory());

		var serializer = (Serializer) obj -> {
			var arrayOutputStream = new ByteArrayOutputStream();
			objectMapper.writeValue(new GZIPOutputStream(arrayOutputStream), obj);
			return arrayOutputStream.toByteArray();
		};

		var connectionId = new AtomicInteger(1);

		var documentStorageServiceChannel =
				Grpc.newChannelBuilder(System.getenv("DOCUMENT_STORAGE_SERVICE_URL"), InsecureChannelCredentials.create())
						.build();

		var documentStorageService = RxDocumentStorageServiceGrpc.newRxStub(documentStorageServiceChannel);

		var editDocumentReactiveRequestHandler = new EditDocumentReactiveRequestHandler(() -> documentStorageService);
		var connectRequestHandler = new ConnectReactiveRequestHandler(connectionId::getAndIncrement, () -> documentStorageService);

		var eventHandlerByEventType = Stream.of(editDocumentReactiveRequestHandler, connectRequestHandler)
				.collect(Collectors.toMap(ReactiveRequestHandler::getHandledRequestType, e -> e));


		var deserializer = new JacksonDeserializer(objectMapper);

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
										.ws("/documents", (wsInbound, wsOutbound) -> {
											var pongSubscription = pongPublisher
													.subscribeOn(Schedulers.parallel())
													.subscribe(v -> {
														try {
															wsOutbound.sendByteArray(Mono.just(serializer.serialize(v)));
														}
														catch (IOException e) {
															throw new RuntimeException(e);
														}
													});

											wsInbound.receiveCloseStatus()
													.subscribeOn(Schedulers.parallel())
													.subscribe(closeStatus -> {
														LOGGER.info("Connection closed with code = {} reason = {}", closeStatus.code(),
																closeStatus.reasonText());
														pongSubscription.dispose();
													});

											return wsInbound
													.aggregateFrames()
													.receiveFrames()
													.map(v -> new ByteBufInputStream(v.content().retain(), true))
													.<ClientRequest> handle((v, sink) -> {
														try {
															sink.next(deserializer.deserialize(v, ClientRequest.class));
														}
														catch (IOException e) {
															sink.error(e);
														}
													})
													.flatMap(request -> {
														LOGGER.info("New client request = {}", request);
														var eventHandler = eventHandlerByEventType.get(request.type());
														if (eventHandler == null) {
															LOGGER.info("No handler for {} event type found...", request.type());
															return Flux.empty();
														} else {
															return wsOutbound.send(eventHandler.handleRequest(Mono.just(request), null)
																	.<byte[]> handle((response, sink) -> {
																		try {
																			sink.next(serializer.serialize(response));
																		}
																		catch (IOException e) {
																			sink.error(e);
																		}
																	})
																	.map(Unpooled::wrappedBuffer));
														}
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
