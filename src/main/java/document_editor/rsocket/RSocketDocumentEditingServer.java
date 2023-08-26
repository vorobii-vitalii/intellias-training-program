package document_editor.rsocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.document.storage.RxDocumentStorageServiceGrpc;
import com.fasterxml.jackson.databind.ObjectMapper;

import document_editor.dto.ClientRequest;
import document_editor.netty_reactor.ReactiveDocumentChangesPublisher;
import document_editor.netty_reactor.request_handling.ReactiveMessageHandler;
import document_editor.netty_reactor.request_handling.impl.ConnectReactiveRequestHandler;
import document_editor.netty_reactor.request_handling.impl.EditDocumentReactiveRequestHandler;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.netty.buffer.ByteBufInputStream;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketServer;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.ServerTransport;
import io.rsocket.transport.netty.WebsocketDuplexConnection;
import io.rsocket.util.ByteBufPayload;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.server.HttpServer;
import serialization.JacksonDeserializer;
import serialization.Serializer;

public class RSocketDocumentEditingServer {
	public static final boolean CLOSE_RESOURCE = true;
	private static final Logger LOGGER = LoggerFactory.getLogger(RSocketDocumentEditingServer.class);

	public static void main(String[] args) {

		var objectMapper = new ObjectMapper(new MessagePackFactory());

		var serializer = (Serializer) obj -> {
			var arrayOutputStream = new ByteArrayOutputStream();
			objectMapper.writeValue(arrayOutputStream, obj);
			return arrayOutputStream.toByteArray();
		};

		var connectionId = new AtomicInteger(1);

		var documentStorageService = RxDocumentStorageServiceGrpc.newRxStub(
				Grpc.newChannelBuilder(System.getenv("DOCUMENT_STORAGE_SERVICE_URL"), InsecureChannelCredentials.create()).build());

		var editDocumentReactiveRequestHandler = new EditDocumentReactiveRequestHandler(() -> documentStorageService);
		var connectRequestHandler = new ConnectReactiveRequestHandler(connectionId::getAndIncrement, () -> documentStorageService,
				new ReactiveDocumentChangesPublisher(() -> documentStorageService));

		var eventHandlerByEventType = Stream.of(editDocumentReactiveRequestHandler, connectRequestHandler)
				.collect(Collectors.toMap(ReactiveMessageHandler::getHandledMessageType, e -> e));

		var deserializer = new JacksonDeserializer(objectMapper);

		ServerTransport.ConnectionAcceptor connectionAcceptor =
				RSocketServer.create(SocketAcceptor.with(new RSocket() {

							@Override
							public Flux<Payload> requestStream(Payload payload) {
								return requestChannel(Mono.just(payload));
							}

							@Override
							public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
								return Flux.from(payloads)
										.map(payload -> new ByteBufInputStream(payload.data().retain(), CLOSE_RESOURCE))
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
												return eventHandler.handleMessage(request, null)
														.<byte[]> handle((response, sink) -> {
															try {
																sink.next(serializer.serialize(response));
															}
															catch (IOException e) {
																sink.error(e);
															}
														})
														.map(ByteBufPayload::create);
											}
										});
							}
						}))
						// TODO: Change to zero copy if feasible
						.payloadDecoder(PayloadDecoder.DEFAULT)
						.asConnectionAcceptor();

		var server = HttpServer.create()
				.port(getPort())
				.handle((req, res) -> res.sendWebsocket((in, out) ->
						connectionAcceptor
								.apply(new WebsocketDuplexConnection((Connection) in.aggregateFrames()))
								.then(out.neverComplete())))
				.bindNow();
		server.onDispose().block();
	}

	private static int getPort() {
		return Optional.ofNullable(System.getenv("PORT"))
				.map(Integer::parseInt)
				.orElse(8000);
	}

}
