package document_editor;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.GZIPOutputStream;

import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.document.storage.DocumentStorageServiceGrpc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;

import document_editor.dto.Request;
import document_editor.dto.RequestType;
import document_editor.event.EditDocumentsEvent;
import document_editor.event.DocumentsEvent;
import document_editor.event.NewConnectionDocumentsEvent;
import document_editor.event.PingDocumentsEvent;
import document_editor.event.SendPongsDocumentsEvent;
import document_editor.event.context.ClientConnectionsContext;
import document_editor.event.handler.impl.EditEventHandler;
import document_editor.event.handler.impl.MessageDistributeEventHandler;
import document_editor.event.handler.impl.NewConnectionEventHandler;
import document_editor.event.handler.impl.PingEventHandler;
import document_editor.event.handler.impl.PongEventHandler;
import grpc.ContextPropagationServiceDecorator;
import http.domain.HTTPMethod;
import http.domain.HTTPRequest;
import http.handler.FileDownloadHTTPHandlerStrategy;
import http.handler.HTTPAcceptOperationHandler;
import http.handler.HTTPNetworkRequestHandler;
import http.post_processor.HTTPResponsePostProcessor;
import http.post_processor.ProtocolChangerHTTPResponsePostProcessor;
import http.reader.HTTPRequestMessageReader;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import message_passing.DisruptorMessageProducer;
import message_passing.MessageProducer;
import monitoring.PrometheusMetricsHTTPRequestHandler;
import request_handler.NetworkRequest;
import request_handler.NetworkRequestHandler;
import serialization.JacksonDeserializer;
import serialization.Serializer;
import tcp.FillQueueProcess;
import tcp.MessageSerializer;
import tcp.server.BufferCopier;
import tcp.server.ByteBufferPool;
import tcp.server.ConnectionImpl;
import tcp.server.Poller;
import tcp.server.RoundRobinProvider;
import tcp.server.ServerAttachment;
import tcp.server.SocketConnection;
import tcp.server.SocketMessageReaderImpl;
import tcp.server.TCPServer;
import tcp.server.TCPServerConfig;
import tcp.server.TimerSocketMessageReader;
import tcp.server.handler.DelegatingReadOperationHandler;
import tcp.server.handler.GenericReadOperationHandler;
import tcp.server.handler.WriteOperationHandler;
import token_bucket.TokenBucket;
import util.Constants;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;
import websocket.endpoint.DelegatingWebSocketEndpoint;
import websocket.endpoint.OnMessageHandler;
import websocket.endpoint.WebSocketEndpointProvider;
import websocket.handler.WebSocketChangeProtocolHTTPHandlerStrategy;
import websocket.handler.WebSocketNetworkRequestHandler;
import websocket.handler.WebSocketProtocolChanger;
import websocket.reader.WebSocketMessageReader;

public class HttpServer {
	public static final String PROMETHEUS_ENDPOINT = "/prometheus";
	public static final int MAX_TOKENS_WRITE = 1000;
	public static final int MAX_TOKENS_READ = 10;
	public static final int SELECTION_TIMEOUT = 1;
	public static final int DOCUMENT_ID = 13;
	private static final AtomicInteger atomicInteger = new AtomicInteger(1);
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpServer.class);
	private static final Integer WS_VERSION = 13;
	public static final List<Predicate<HTTPRequest>> WEBSOCKET_HANDSHAKE_REQUEST_PREDICATE = List.of(
			request -> request.getHttpRequestLine().httpMethod() == HTTPMethod.GET,
			request -> request.getHeaders()
					.getHeaderValue(Constants.HTTPHeaders.HOST)
					.isPresent(),
			request -> request.getHeaders()
					.getHeaderValue(Constants.HTTPHeaders.UPGRADE)
					.filter("websocket"::equals)
					.isPresent(),
			request -> request.getHeaders()
					.getHeaderValue(Constants.HTTPHeaders.CONNECTION)
					.filter("Upgrade"::equalsIgnoreCase)
					.isPresent(),
			request -> request.getHeaders()
					.getHeaderValue(Constants.HTTPHeaders.WEBSOCKET_KEY)
					.isPresent(),
			request -> request.getHeaders().getHeaderValue(Constants.HTTPHeaders.WEBSOCKET_VERSION)
					.filter(String.valueOf(WS_VERSION)::equals)
					.isPresent()
	);
	public static final int MAX_WAIT_FOR_PING_MS = 15_000;
	public static final int RING_BUFFER_SIZE = (int) Math.pow(2, 10);

	private static int getPort() {
		return Optional.ofNullable(System.getenv("PORT"))
				.map(Integer::parseInt)
				.orElse(8000);
	}

	private static String getHost() {
		return Optional.ofNullable(System.getenv("HOST"))
				.orElse("127.0.0.1");
	}

	private static void startProcess(Runnable process, String processName) {
		var thread = new Thread(process);
		thread.setName(processName);
		thread.start();
	}

	private static Selector[] createSelectors(int n) throws IOException {
		Selector[] selectors = new Selector[n];
		for (int i = 0; i < n; i++) {
			selectors[i] = Selector.open();
		}
		return selectors;
	}

	private static void schedulePeriodically(int delayMs, Runnable process) {
		var executor = Executors.newSingleThreadScheduledExecutor();
		executor.scheduleWithFixedDelay(process, delayMs, delayMs, MILLISECONDS);
	}

	private static <T> RingBuffer<NetworkRequest<T>> createNetworkDisruptor(NetworkRequestHandler<T> requestHandler) {
		ThreadFactory threadFactory = DaemonThreadFactory.INSTANCE;

		WaitStrategy waitStrategy = new BusySpinWaitStrategy();
		Disruptor<NetworkRequest<T>> disruptor = new Disruptor<>(
				NetworkRequest::new,
				RING_BUFFER_SIZE,
				threadFactory,
				ProducerType.MULTI,
				waitStrategy
		);
		disruptor.handleEventsWith((tNetworkRequest, l, b) -> {
			try {
				requestHandler.handle(tNetworkRequest);
			}
			catch (Exception error) {
				if (!(error instanceof IOException) && !(error instanceof CancelledKeyException)) {
					LOGGER.warn("Error occurred", error);
				}
			}
		});
		return disruptor.start();
	}

	public static void main(String[] args) throws IOException {


		var resource = Resource.getDefault()
				.merge(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "documents-app")));

		var spanExporter = OtlpGrpcSpanExporter.builder()
				.setEndpoint(System.getenv("JAEGER_ENDPOINT"))
				.setTimeout(Duration.ofSeconds(15))
				.build();

		var sdkTracerProvider = SdkTracerProvider.builder()
				.addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
				.setResource(resource)
				.build();

		var sdkMeterProvider = SdkMeterProvider.builder()
				.registerMetricReader(PeriodicMetricReader.builder(OtlpGrpcMetricExporter.builder().build()).build())
				.setResource(resource)
				.build();

		var openTelemetry = OpenTelemetrySdk.builder()
				.setTracerProvider(sdkTracerProvider)
				.setMeterProvider(sdkMeterProvider)
				.setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
				.buildAndRegisterGlobal();

		var objectMapper = new ObjectMapper(new MessagePackFactory());

		var eventsQueue = new ConcurrentLinkedQueue<DocumentsEvent>();

		var prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

		MessageProducer<DocumentsEvent> messageProducer = eventsQueue::add;

		var messageHandler = new OnMessageHandler<>(
				new JacksonDeserializer(objectMapper),
				Request.class,
				new DelegatingRequestHandler(Map.of(
						RequestType.CONNECT, (r, c) -> messageProducer.produce(new NewConnectionDocumentsEvent(c)),
						RequestType.CHANGES, (r, c) -> messageProducer.produce(new EditDocumentsEvent(r.payload())),
						RequestType.PING, (r, c) -> messageProducer.produce(new PingDocumentsEvent(c))
				)),
				error -> LOGGER.error("Error on deserialization", error));
		var webSocketEndpointProvider = new WebSocketEndpointProvider(Map.of(
				"/documents", new DelegatingWebSocketEndpoint(Map.of(
						OpCode.BINARY, messageHandler,
						OpCode.CONTINUATION, messageHandler,
						OpCode.TEXT, messageHandler,
						OpCode.CONNECTION_CLOSE, (s, m) -> {
							var webSocketMessage = new WebSocketMessage();
							webSocketMessage.setFin(true);
							webSocketMessage.setOpCode(OpCode.CONNECTION_CLOSE);
							webSocketMessage.setPayload(new byte[] {});
							s.appendResponse(webSocketMessage, null, SocketConnection::close);
							s.changeOperation(OP_WRITE);
						}
				), s -> {
//					LOGGER.info("Connected {}", s)
				})
		));

		var webSocketChangeProtocolHTTPHandlerStrategy =
				new WebSocketChangeProtocolHTTPHandlerStrategy(WEBSOCKET_HANDSHAKE_REQUEST_PREDICATE, webSocketEndpointProvider);

		Collection<HTTPResponsePostProcessor> httpResponsePostProcessors = List.of(
				new ProtocolChangerHTTPResponsePostProcessor(List.of(new WebSocketProtocolChanger(webSocketEndpointProvider)))
		);

		new ProcessorMetrics().bindTo(prometheusRegistry);
		new JvmMemoryMetrics().bindTo(prometheusRegistry);
		new JvmGcMetrics().bindTo(prometheusRegistry);
		new JvmThreadMetrics().bindTo(prometheusRegistry);

		var httpRequestParseTimer = Timer.builder("http.request.parse.time")
				.description("Time to parse HTTP request message")
				.register(prometheusRegistry);

		var webSocketRequestParseTimer = Timer.builder("websocket.request.parse.time")
				.description("Time to parse WebSocket request message")
				.register(prometheusRegistry);

		var messagesWriteTimer = Timer.builder("messages.write.time")
				.description("Time to write into socket ")
				.register(prometheusRegistry);

		var httpRequestProcessingTimer = Timer.builder("http.request.processing")
				.description("Time to process single HTTP request")
				.register(prometheusRegistry);

		var changesApplyTimer = Timer.builder("db.write.changes.time")
				.description("Time to write changes to DB")
				.register(prometheusRegistry);

		var dbReadDocumentTimer = Timer.builder("db.read.document.time")
				.description("Time to read a single document from DB")
				.register(prometheusRegistry);

		var messagesWrittenCounter = Counter.builder("messages.written").description("Number of written messages").register(prometheusRegistry);

		var webSocketRequestProcessingTimer = Timer.builder("websocket.request.processing")
				.description("Time to process single WebSocket request")
				.register(prometheusRegistry);

		var httpRequestsCount = Counter.builder("http.requests.count").register(prometheusRegistry);
		var webSocketRequestsCount = Counter.builder("websocket.requests.count").register(prometheusRegistry);

		Set<TokenBucket<SocketAddress>> tokenBuckets = Collections.synchronizedSet(new HashSet<>());

		Selector[] wsSelectors = createSelectors(4);
		Selector[] httpSelectors = createSelectors(4);

		RoundRobinProvider<Selector> webSocketSelectorProvider = new RoundRobinProvider<>(wsSelectors);

		Consumer<SocketConnection> changeSelector = connection -> connection.changeSelector(webSocketSelectorProvider.get());

		var byteBufferPool = new ByteBufferPool(ByteBuffer::allocateDirect);
		var appByteBufferPool = new ByteBufferPool(ByteBuffer::allocate);

		var httpRequestHandler = new HTTPNetworkRequestHandler(
				List.of(
						webSocketChangeProtocolHTTPHandlerStrategy,
						new FileDownloadHTTPHandlerStrategy(p -> p.isEmpty() || p.equals("/"), "index.html", "text/html"),
						new PrometheusMetricsHTTPRequestHandler(prometheusRegistry, PROMETHEUS_ENDPOINT)
				),
				httpResponsePostProcessors,
				List.of(response -> {
					if (response.isUpgradeResponse()) {
						return changeSelector;
					}
					return null;
				}),
				new MessageSerializer(byteBufferPool),
				openTelemetry
		);
		var webSocketNetworkRequestHandler = new WebSocketNetworkRequestHandler(webSocketEndpointProvider);

		var httpRing = createNetworkDisruptor(httpRequestHandler);
		var wsRing = createNetworkDisruptor(webSocketNetworkRequestHandler);

		var eventContext = new ClientConnectionsContext(MAX_WAIT_FOR_PING_MS, Instant::now, new MessageSerializer(byteBufferPool),
				new BufferCopier(byteBufferPool));

		var documentStorageServiceChannel =
				Grpc.newChannelBuilder(
								System.getenv("DOCUMENT_STORAGE_SERVICE_URL"),
								InsecureChannelCredentials.create())
						.maxInboundMessageSize(Integer.MAX_VALUE)
						.build();

		var documentStorageService = DocumentStorageServiceGrpc.newStub(documentStorageServiceChannel);

		schedulePeriodically(2000, new FillQueueProcess<>(eventsQueue, new SendPongsDocumentsEvent()));

		var contextPropagationServiceDecorator = new ContextPropagationServiceDecorator(openTelemetry);

		schedulePeriodically(250, new DocumentMessageEventsHandler(eventsQueue, eventContext, List.of(
				new NewConnectionEventHandler(
						documentStorageService,
						atomicInteger::getAndIncrement,
						objectMapper,
						openTelemetry.getTracer("New connection handler"),
						contextPropagationServiceDecorator,
						new MessageSerializer(byteBufferPool),
						new BufferCopier(byteBufferPool)
				),
				new MessageDistributeEventHandler(),
				new PingEventHandler(),
				new PongEventHandler(objectMapper),
				new EditEventHandler(documentStorageService, openTelemetry.getTracer("Edit event handler"),
						contextPropagationServiceDecorator)
		)));
		var serializer = (Serializer) obj -> {
			var arrayOutputStream = new ByteArrayOutputStream();
			objectMapper.writeValue(new GZIPOutputStream(arrayOutputStream), obj);
			return arrayOutputStream.toByteArray();
		};
		startProcess(new DocumentChangeWatcher(messageProducer, serializer, documentStorageService), "Document change watcher");

		Consumer<SelectionKey> closeConnection = selectionKey -> {
			var socketConnection = new ConnectionImpl((ServerAttachment) selectionKey.attachment());
//			socketConnection.close();
		};

		BiConsumer<SelectionKey, Throwable> onError = (selectionKey, throwable) -> {
			var socketConnection = ((ServerAttachment) selectionKey.attachment()).toSocketConnection();
			LOGGER.warn("Error occurred", throwable);
		};

		Map<Integer, Consumer<SelectionKey>> operationHandlerByTypeHTTP =
				Map.of(
						OP_WRITE, new WriteOperationHandler(
								messagesWriteTimer,
								messagesWrittenCounter,
								onError,
								byteBufferPool,
								openTelemetry
						),
						OP_READ, new DelegatingReadOperationHandler(Map.of(
								Constants.Protocol.HTTP, new GenericReadOperationHandler<>(
										new DisruptorMessageProducer<>(httpRing),
										new TimerSocketMessageReader<>(
												httpRequestParseTimer,
												new SocketMessageReaderImpl<>(
														new HTTPRequestMessageReader(
																(name, val) -> Collections.singletonList(val.toString().trim()))
												)),
										onError,
										openTelemetry.getTracer("HTTP request reader"),
										Context::current
								),
								Constants.Protocol.WEB_SOCKET, new GenericReadOperationHandler<>(
										new DisruptorMessageProducer<>(wsRing),
										new TimerSocketMessageReader<>(webSocketRequestParseTimer, new SocketMessageReaderImpl<>(new WebSocketMessageReader())),
										onError,
										openTelemetry.getTracer("WebSocket request reader"),
										Context::current
								)))
				);
		Map<Integer, Consumer<SelectionKey>> operationHandlerByTypeWebSockets =
				Map.of(
						OP_READ, new GenericReadOperationHandler<>(
								new DisruptorMessageProducer<>(wsRing),
								new TimerSocketMessageReader<>(webSocketRequestParseTimer,
										new SocketMessageReaderImpl<>(new WebSocketMessageReader())),
								onError,
								openTelemetry.getTracer("WebSocket request reader"),
								Context::current
						),
						OP_WRITE, new WriteOperationHandler(
								messagesWriteTimer,
								messagesWrittenCounter,
								onError,
								byteBufferPool,
								openTelemetry
						)
				);

		for (int i = 0; i < wsSelectors.length; i++) {
			startProcess(new Poller(wsSelectors[i], operationHandlerByTypeWebSockets, closeConnection, SELECTION_TIMEOUT),
					"WebSocket selector " + i);
		}
		for (int i = 0; i < httpSelectors.length; i++) {
			startProcess(new Poller(httpSelectors[i], operationHandlerByTypeHTTP, closeConnection, SELECTION_TIMEOUT), "HTTP selector " + i);
		}

		RoundRobinProvider<Selector> httpSelectorProvider = new RoundRobinProvider<>(httpSelectors);

		var server = new TCPServer(
				TCPServerConfig.builder()
						.setHost(getHost())
						.setPort(getPort())
						.setProtocolFamily(StandardProtocolFamily.INET)
						.onConnectionClose(closeConnection)
						.build(),
				SelectorProvider.provider(),
				System.err::println,
				// Yourkit
				Map.of(
						OP_ACCEPT, new HTTPAcceptOperationHandler(
								tokenBuckets,
								MAX_TOKENS_WRITE,
								MAX_TOKENS_READ,
								selectionKey -> httpSelectorProvider.get(),
								byteBufferPool,
								openTelemetry.getTracer("HTTP accept connection handler"),
								appByteBufferPool
						)
				));
		server.start();
	}

}
