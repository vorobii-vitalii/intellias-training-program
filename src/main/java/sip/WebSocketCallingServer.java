package sip;

import static document_editor.HttpServer.WEBSOCKET_HANDSHAKE_REQUEST_PREDICATE;
import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

import org.kurento.client.KurentoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import http.domain.HTTPRequest;
import http.handler.HTTPAcceptOperationHandler;
import http.handler.HTTPNetworkRequestHandler;
import http.post_processor.ProtocolChangerHTTPResponsePostProcessor;
import http.reader.HTTPRequestMessageReader;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import message_passing.BlockingQueueMessageProducer;
import request_handler.NetworkRequest;
import request_handler.RequestHandler;
import request_handler.RequestProcessor;
import serialization.JacksonDeserializer;
import serialization.Serializer;
import sip.request_handling.AcceptingCallSipResponsePreProcessor;
import sip.request_handling.BindingUpdateResponsePreProcessor;
import sip.request_handling.ConfirmParticipantOffersResponseHandler;
import sip.request_handling.DestroyingCallSipResponsePostProcessor;
import sip.request_handling.InMemoryInviteDialogService;
import sip.request_handling.SDPMediaAddressProcessor;
import sip.request_handling.SipMessageNetworkRequestHandler;
import sip.request_handling.SipResponseHandler;
import sip.request_handling.calls.InMemoryCallsRepository;
import sip.request_handling.enricher.CompositeUpdater;
import sip.request_handling.enricher.ContactUnknownAttributesRemover;
import sip.request_handling.invite.CreateConferenceRequestHandler;
import sip.request_handling.invite.InviteRequestHandler;
import sip.request_handling.invite.JoinConferenceRequestHandler;
import sip.request_handling.invite.KurentoMediaConferenceService;
import sip.request_handling.invite.ConferenceSubscribersContext;
import sip.request_handling.invite.SubscribeRequestHandler;
import sip.request_handling.media.InMemoryMediaMappingStorage;
import sip.request_handling.media.MediaCallInitiator;
import sip.request_handling.normalize.Normalizer;
import sip.request_handling.normalize.SipRequestViaParameterNormalizer;
import sip.request_handling.register.AckRequestHandler;
import sip.request_handling.register.ByeRequestProcessor;
import sip.request_handling.register.InMemoryBindingStorage;
import sip.request_handling.register.RegisterSipMessageHandler;
import tcp.server.impl.MessageSerializerImpl;
import websocket.WebSocketFramerMessageSerializer;
import tcp.server.ByteBufferPool;
import tcp.server.GenericServer;
import tcp.server.ServerConfig;
import tcp.server.SocketConnection;
import tcp.server.SocketMessageReaderImpl;
import tcp.server.TCPServerConfigurer;
import tcp.server.TimerSocketMessageReader;
import tcp.server.handler.DelegatingReadOperationHandler;
import tcp.server.handler.GenericReadOperationHandler;
import tcp.server.handler.WriteOperationHandler;
import udp.ByteBufferSource;
import util.Constants;
import websocket.domain.WebSocketMessage;
import websocket.endpoint.WebSocketEndpoint;
import websocket.endpoint.WebSocketEndpointProvider;
import websocket.handler.WebSocketChangeProtocolHTTPHandlerStrategy;
import websocket.handler.WebSocketNetworkRequestHandler;
import websocket.handler.WebSocketProtocolChanger;
import websocket.reader.WebSocketMessageReader;

public class WebSocketCallingServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketCallingServer.class);
	public static final int BUFFER_EXPIRATION_TIME_MILLIS = 10_000;
	public static final int REQUEST_QUEUE_CAPACITY = 10_000;
	public static final SimpleMeterRegistry METER_REGISTRY = new SimpleMeterRegistry();
	public static final InMemoryMediaMappingStorage MEDIA_MAPPING_STORAGE = new InMemoryMediaMappingStorage();
	public static final Set<String> SUPPORTED_PROTOCOLS = Set.of("sip");
	public static final Tracer TRACER = TracerProvider.noop().get("");
	public static final String CONFERENCE_FACTORY = "conference-factory";

	private static void startProcess(Runnable process, String processName) {
		var thread = new Thread(process);
		thread.setName(processName);
		thread.setUncaughtExceptionHandler((t, e) -> LOGGER.error("Error in thread {}", t, e));
		thread.start();
	}

	private static final ByteBufferPool BUFFER_POOL = new ByteBufferPool(ByteBuffer::allocate, BUFFER_EXPIRATION_TIME_MILLIS);
	private static final MessageSerializerImpl MESSAGE_SERIALIZER = new MessageSerializerImpl(BUFFER_POOL);

	public static void main(String[] args) throws IOException {
		startSipServer();
	}

	private static void startSipServer() throws IOException {
		var httpRequestsQueue = new ArrayBlockingQueue<NetworkRequest<HTTPRequest>>(REQUEST_QUEUE_CAPACITY);
		var webSocketRequestsQueue = new ArrayBlockingQueue<NetworkRequest<WebSocketMessage>>(REQUEST_QUEUE_CAPACITY);
		var requestQueue = new ArrayBlockingQueue<NetworkRequest<SipMessage>>(REQUEST_QUEUE_CAPACITY);

		var httpRequestHandleTimer = Timer.builder("http.request.time").register(METER_REGISTRY);
		var httpRequestCount = Counter.builder("http.request.count").register(METER_REGISTRY);

		var selectorProvider = SelectorProvider.provider();

		var bindingStorage = new InMemoryBindingStorage();
		var callsRepository = new InMemoryCallsRepository();
		List<SDPMediaAddressProcessor> sdpMediaAddressProcessors = List.of();
		var mediaCallInitiator = new MediaCallInitiator(MEDIA_MAPPING_STORAGE);
		var contactUnknownAttributesRemover = new ContactUnknownAttributesRemover();
		var sipRequestUpdater = new CompositeUpdater<>(
				List.of(contactUnknownAttributesRemover));

		var kurentoClient = KurentoClient.create();
		var mediaConferenceService = new KurentoMediaConferenceService(kurentoClient);

		var messageSerializer = new WebSocketFramerMessageSerializer(MESSAGE_SERIALIZER);

		final Serializer serializer = obj -> new Gson().toJson(obj).getBytes(StandardCharsets.UTF_8);
		final ConferenceSubscribersContext conferenceSubscribersContext = new ConferenceSubscribersContext(messageSerializer,
				mediaConferenceService, serializer);
		var dialogService = new InMemoryInviteDialogService();
		RequestHandler<NetworkRequest<SipMessage>> sipRequestHandler = new SipMessageNetworkRequestHandler(
				List.of(
						new RegisterSipMessageHandler(messageSerializer, bindingStorage),
						new CreateConferenceRequestHandler(
								addressOfRecord -> {
									var sipURI = addressOfRecord.sipURI();
									return sipURI.credentials().username().equals(CONFERENCE_FACTORY);
								},
								() -> "conference-" + UUID.randomUUID(),
								messageSerializer,
								mediaConferenceService
						),
						new JoinConferenceRequestHandler(mediaConferenceService, messageSerializer, conferenceSubscribersContext, dialogService),
						new InviteRequestHandler(
								bindingStorage,
								messageSerializer,
								sdpMediaAddressProcessors,
								callsRepository,
								sipRequestUpdater
						),
						new AckRequestHandler(
								bindingStorage,
								messageSerializer,
								callsRepository,
								mediaCallInitiator,
								sipRequestUpdater
						),
						new ByeRequestProcessor(
								callsRepository,
								bindingStorage,
								messageSerializer,
								sipRequestUpdater
						),
						new SubscribeRequestHandler(
								conferenceSubscribersContext,
								messageSerializer
						)
				),

				List.of(
						new ConfirmParticipantOffersResponseHandler(mediaConferenceService, new JacksonDeserializer(new ObjectMapper())),
						new SipResponseHandler(
								List.of(
										new BindingUpdateResponsePreProcessor(callsRepository),
										new AcceptingCallSipResponsePreProcessor(callsRepository),
										new DestroyingCallSipResponsePostProcessor(callsRepository)
								),
								messageSerializer,
								callsRepository
						)
				),
				new Normalizer<>(List.of(new SipRequestViaParameterNormalizer())));

		RequestProcessor<NetworkRequest<SipMessage>> requestProcessor =
				new RequestProcessor<>(requestQueue, sipRequestHandler, Timer.builder("124").register(METER_REGISTRY),
						Counter.builder("123").register(METER_REGISTRY));

		startProcess(requestProcessor, "SIP request processor");

		var sipMessageReader = new SipMessageReader();


		var webSocketEndpointProvider = new WebSocketEndpointProvider(Map.of(
				"/", new WebSocketEndpoint() {
					@Override
					public void onHandshakeCompletion(SocketConnection connection) {

					}

					@Override
					public void onMessage(SocketConnection connection, WebSocketMessage message) {
						var bytesSource = new ByteBufferSource(ByteBuffer.wrap(message.getPayload()));
						var readResult = sipMessageReader.read(bytesSource, e -> {
						});
						if (readResult == null) {
							LOGGER.info("Message cannot be parser... {}", message);
							return;
						}
						var msg = readResult.first();
						try {
							requestQueue.put(new NetworkRequest<>(msg, connection));
						}
						catch (InterruptedException e) {
							throw new RuntimeException(e);
						}
					}
				}
		));
		HTTPNetworkRequestHandler httpRequestHandler = new HTTPNetworkRequestHandler(
				Executors.newFixedThreadPool(8),
				List.of(
						new WebSocketChangeProtocolHTTPHandlerStrategy(
								WEBSOCKET_HANDSHAKE_REQUEST_PREDICATE,
								webSocketEndpointProvider,
								SUPPORTED_PROTOCOLS
						)
				),
				List.of(new ProtocolChangerHTTPResponsePostProcessor(List.of(
								new WebSocketProtocolChanger(webSocketEndpointProvider, SUPPORTED_PROTOCOLS)
				))),
				MESSAGE_SERIALIZER,
				TRACER,
				Context::current
		);

		final WebSocketNetworkRequestHandler webSocketNetworkRequestHandler = new WebSocketNetworkRequestHandler(webSocketEndpointProvider);

		startProcess(new RequestProcessor<>(httpRequestsQueue, httpRequestHandler, httpRequestHandleTimer, httpRequestCount), "HTTP server");
		startProcess(new RequestProcessor<>(webSocketRequestsQueue, webSocketNetworkRequestHandler, httpRequestHandleTimer, httpRequestCount), "WS server");

		BiConsumer<SelectionKey, Throwable> onError = (selectionKey, throwable) -> LOGGER.error("Error {}", selectionKey, throwable);

		var server = new GenericServer(
				ServerConfig.builder()
						.setHost(getHost())
						.setPort(getSipServerPort())
						.setProtocolFamily(StandardProtocolFamily.INET)
						.setInterestOps(OP_ACCEPT)
						.onConnectionClose(connection -> {
							LOGGER.info("Connection closed");
						})
						.build(),
				selectorProvider,
				System.err::println,
				Map.of(
						OP_ACCEPT, new HTTPAcceptOperationHandler(
								SelectionKey::selector,
								BUFFER_POOL,
								TRACER
						),
						OP_READ, new DelegatingReadOperationHandler(Map.of(
								Constants.Protocol.HTTP, new GenericReadOperationHandler<>(
										new BlockingQueueMessageProducer<>(httpRequestsQueue),
										new TimerSocketMessageReader<>(
												Timer.builder("http.request.parse").register(METER_REGISTRY),
												new SocketMessageReaderImpl<>(
														new HTTPRequestMessageReader(
																(name, val) -> Collections.singletonList(val.toString().trim()))
												)),
										onError,
										TRACER,
										Context::current
								),
								Constants.Protocol.WEB_SOCKET, new GenericReadOperationHandler<>(
										new BlockingQueueMessageProducer<>(webSocketRequestsQueue),
										new TimerSocketMessageReader<>(Timer.builder("ws.request.parse").register(METER_REGISTRY),
												new SocketMessageReaderImpl<>(new WebSocketMessageReader())),
										onError,
										TRACER,
										Context::current
								))),
						OP_WRITE, new WriteOperationHandler(
								Timer.builder("msg.write").register(METER_REGISTRY),
								Counter.builder("msg.count").register(METER_REGISTRY),
								onError,
								BUFFER_POOL,
								OpenTelemetry.noop()
						)

				),
				new TCPServerConfigurer());
		server.start();
	}

	private static int getSipServerPort() {
		return Optional.ofNullable(System.getenv("SIP_PORT"))
				.map(Integer::parseInt)
				.orElse(5068);
	}

	private static String getHost() {
		return Optional.ofNullable(System.getenv("HOST"))
				.orElse("127.0.0.1");
	}

}
