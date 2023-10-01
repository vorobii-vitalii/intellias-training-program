package document_editor.netty_reactor;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import document_editor.dto.ClientRequest;
import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import document_editor.netty_reactor.dependency_injection.components.DaggerDeserializerComponent;
import document_editor.netty_reactor.dependency_injection.components.DaggerMessageHandlerComponent;
import document_editor.netty_reactor.dependency_injection.components.DaggerMetricsComponent;
import document_editor.netty_reactor.dependency_injection.components.DaggerSerializerComponent;
import io.micrometer.core.instrument.Metrics;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.LoopResources;
import request_handler.ReactiveMessageHandler;

public class DocumentEditingServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentEditingServer.class);
	public static final String PROMETHEUS_ENDPOINT = "/prometheus";
	public static final int PONG_INTERVAL = 5000;
	private static final Response PONG_RESPONSE = new Response(ResponseType.PONG, null);
	public static final String DOCUMENTS_ENDPOINT = "/documents";

	public static void main(String[] args) {
		var pongPublisher = new PeriodicalPublisherCreator<>(PONG_INTERVAL, () -> PONG_RESPONSE).create();

		var serializer = DaggerSerializerComponent.create().createSerializer();
		var deserializer = DaggerDeserializerComponent.create().createDeserializer();
		var messageHandlers = DaggerMessageHandlerComponent.builder()
				.withDocumentStorageServiceURI(getDocumentStorageServiceUrl()).build()
				.getMessageHandlers();
		var registry = DaggerMetricsComponent.create().getMeterRegistry();

		var eventHandlerByEventType = messageHandlers.stream()
				.collect(Collectors.toMap(ReactiveMessageHandler::getHandledMessageType, e -> e));
		Metrics.addRegistry(registry);

		DisposableServer server =
				HttpServer.create()
						.runOn(LoopResources.create("event-loop", 2, 8, true))
						.port(getPort())
						.accessLog(false)
						.metrics(true, s -> s)
						.noSSL()
						.protocol(HttpProtocol.HTTP11)
						.route(routes ->
								routes
										.get(PROMETHEUS_ENDPOINT, (request, response) -> {
											LOGGER.info("Returning metrics...");
											return response.sendString(Mono.just(registry.scrape()));
										})
										.ws(DOCUMENTS_ENDPOINT, (wsInbound, wsOutbound) -> {
											var pongSubscription = pongPublisher
													.subscribeOn(Schedulers.parallel())
													.subscribe(v -> {
														try {
															LOGGER.info("Sending PONG response");
															wsOutbound.sendByteArray(Mono.just(serializer.serialize(v)))
																	.subscribe(new BaseSubscriber<>() {
																		@Override
																		protected void hookOnError(Throwable throwable) {
																			LOGGER.warn("Send of pong failed...", throwable);
																		}
																		@Override
																		protected void hookOnComplete() {
																			LOGGER.warn("Pong was sent successfully!");
																		}
																	});
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
															return wsOutbound.send(eventHandler.handleMessage(request, null)
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

	private static String getDocumentStorageServiceUrl() {
		return System.getenv("DOCUMENT_STORAGE_SERVICE_URL");
	}

	private static int getPort() {
		return Optional.ofNullable(System.getenv("PORT"))
				.map(Integer::parseInt)
				.orElse(8000);
	}

}
