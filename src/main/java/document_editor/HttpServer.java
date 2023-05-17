package document_editor;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.document.storage.DocumentStorageServiceGrpc;
import com.fasterxml.jackson.databind.ObjectMapper;

import document_editor.event.DisconnectEvent;
import document_editor.event.Event;
import document_editor.event.EventContext;
import document_editor.event.handler.DisconnectEventHandler;
import document_editor.event.handler.EditEventHandler;
import document_editor.event.handler.MessageDistributeEventHandler;
import document_editor.event.handler.NewConnectionEventHandler;
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
import monitoring.PrometheusMetricsHTTPRequestHandler;
import request_handler.NetworkRequest;
import request_handler.NetworkRequestProcessor;
import tcp.server.ByteBufferPool;
import tcp.server.ConnectionImpl;
import tcp.server.Poller;
import tcp.server.RoundRobinProvider;
import tcp.server.ServerAttachment;
import tcp.server.SocketMessageReader;
import tcp.server.TCPServer;
import tcp.server.TCPServerConfig;
import tcp.server.handler.DelegatingReadOperationHandler;
import tcp.server.handler.GenericReadOperationHandler;
import tcp.server.handler.WriteOperationHandler;
import token_bucket.TokenBucket;
import util.Constants;
import util.UnsafeConsumer;
import websocket.domain.WebSocketMessage;
import websocket.endpoint.WebSocketEndpointProvider;
import websocket.handler.WebSocketChangeProtocolHTTPHandlerStrategy;
import websocket.handler.WebSocketNetworkRequestHandler;
import websocket.handler.WebSocketProtocolChanger;
import websocket.reader.WebSocketMessageReader;

public class HttpServer {
	public static final int QUEUE_SIZE = 200_000;
	public static final String PROMETHEUS_ENDPOINT = "/prometheus";
	public static final int MAX_TOKENS_WRITE = 1000;
	public static final int MAX_TOKENS_READ = 10;
	public static final int SELECTION_TIMEOUT = 100;
	public static final int DOCUMENT_ID = 13;
	private static final AtomicInteger atomicInteger = new AtomicInteger(1);
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpServer.class);
	private static final Integer WS_VERSION = 13;
	private static final ScheduledExecutorService documentEventsExecutor = Executors.newSingleThreadScheduledExecutor();

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

		var httpRequestQueue = new ArrayBlockingQueue<NetworkRequest<HTTPRequest>>(QUEUE_SIZE);
		var webSocketRequestQueue = new ArrayBlockingQueue<NetworkRequest<WebSocketMessage>>(QUEUE_SIZE);
		var eventsQueue = new ArrayBlockingQueue<Event>(QUEUE_SIZE);

		var prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

		var webSocketEndpointProvider = new WebSocketEndpointProvider(Map.of(
				"/documents", new DocumentStreamingWebSocketEndpoint(eventsQueue, objectMapper)
		));

		var webSocketChangeProtocolHTTPHandlerStrategy = new WebSocketChangeProtocolHTTPHandlerStrategy(List.of(
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
		), webSocketEndpointProvider);

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

		Selector[] wsSelectors = createSelectors(8);
		Selector[] httpSelectors = createSelectors(8);

		RoundRobinProvider<Selector> webSocketSelectorProvider = new RoundRobinProvider<>(wsSelectors);

		UnsafeConsumer<SelectionKey> changeSelector = selectionKey -> {
			var attachment = (ServerAttachment) selectionKey.attachment();
			var channel = selectionKey.channel();
			var newSelector = webSocketSelectorProvider.get();
			final SelectionKey newSelectionKey = channel.register(newSelector, OP_READ, attachment);
			attachment.setSelectionKey(newSelectionKey);
			newSelector.wakeup();
			selectionKey.cancel();
		};

		var handler = new HTTPNetworkRequestHandler(
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
				openTelemetry
		);

		var httpRequestProcessor = new NetworkRequestProcessor<>(httpRequestQueue, handler, httpRequestProcessingTimer, httpRequestsCount);
		var webSocketRequestProcessor = new NetworkRequestProcessor<>(
				webSocketRequestQueue,
				new WebSocketNetworkRequestHandler(webSocketEndpointProvider),
				webSocketRequestProcessingTimer,
				webSocketRequestsCount
		);
		var eventContext = new EventContext();

		//		var documentStorageServiceChannel = new PooledChannel(100, () -> {
		//			return Grpc.newChannelBuilder(System.getenv("DOCUMENT_STORAGE_SERVICE_URL"), InsecureChannelCredentials.create())
		//					.build();
		//		});

		var documentStorageServiceChannel =
				Grpc.newChannelBuilder(
								System.getenv("DOCUMENT_STORAGE_SERVICE_URL"),
								InsecureChannelCredentials.create())
						.maxInboundMessageSize(Integer.MAX_VALUE)
//						.intercept(new ContextPropagator())
						.build();

		DocumentStorageServiceGrpc.DocumentStorageServiceStub documentStorageService =
				DocumentStorageServiceGrpc.newStub(documentStorageServiceChannel);

		//		refillExecutor.scheduleWithFixedDelay(new RefillProcess<>(tokenBuckets), 1, 1, SECONDS);

		startProcess(httpRequestProcessor, "HTTP Request processor");
		documentEventsExecutor.scheduleWithFixedDelay(new DocumentMessageEventsHandler(eventsQueue, eventContext, List.of(
				new DisconnectEventHandler(),
				new NewConnectionEventHandler(documentStorageService, atomicInteger::getAndIncrement, objectMapper, dbReadDocumentTimer,
						openTelemetry),
				new MessageDistributeEventHandler(),
				new EditEventHandler(documentStorageService, changesApplyTimer, openTelemetry)
		)), 500, 500, MILLISECONDS);
		startProcess(webSocketRequestProcessor, "WebSocket Request processor");
		startProcess(new DocumentChangeWatcher(eventsQueue, objectMapper, documentStorageService, openTelemetry), "Document change watcher");

		Consumer<SelectionKey> closeConnection = selectionKey -> {
			var socketConnection = new ConnectionImpl((ServerAttachment) selectionKey.attachment());
			LOGGER.debug("Closing connection {}", socketConnection);
			socketConnection.close();
			try {
				eventsQueue.put(new DisconnectEvent(socketConnection));
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		};

		BiConsumer<SelectionKey, Throwable> onError = (selectionKey, throwable) -> {
			var socketConnection = new ConnectionImpl((ServerAttachment) selectionKey.attachment());
			LOGGER.info("Closing connection {} because of", socketConnection, throwable);
			socketConnection.close();
			try {
				eventsQueue.put(new DisconnectEvent(socketConnection));
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		};

		var byteBufferPool = new ByteBufferPool(ByteBuffer::allocateDirect);

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
										httpRequestQueue,
										new SocketMessageReader<>(
												new HTTPRequestMessageReader((name, val) -> Collections.singletonList(val.toString().trim())),
												httpRequestParseTimer
										),
										onError,
										openTelemetry
								),
								Constants.Protocol.WEB_SOCKET, new GenericReadOperationHandler<>(
										webSocketRequestQueue,
										new SocketMessageReader<>(new WebSocketMessageReader(), webSocketRequestParseTimer),
										onError,
										openTelemetry
								)
						))
				);
		Map<Integer, Consumer<SelectionKey>> operationHandlerByTypeWebSockets =
				Map.of(
						OP_READ, new GenericReadOperationHandler<>(
								webSocketRequestQueue,
								new SocketMessageReader<>(new WebSocketMessageReader(), webSocketRequestParseTimer),
								onError,
								openTelemetry
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
								openTelemetry,
								byteBufferPool
						),
						OP_WRITE, new WriteOperationHandler(
								messagesWriteTimer,
								messagesWrittenCounter,
								onError,
								byteBufferPool,
								openTelemetry
						),
						OP_READ, new DelegatingReadOperationHandler(Map.of(
								Constants.Protocol.HTTP, new GenericReadOperationHandler<>(
										httpRequestQueue,
										new SocketMessageReader<>(
												new HTTPRequestMessageReader((name, val) -> Collections.singletonList(val.toString().trim())),
												httpRequestParseTimer
										),
										onError,
										openTelemetry
								),
								Constants.Protocol.WEB_SOCKET, new GenericReadOperationHandler<>(
										webSocketRequestQueue,
										new SocketMessageReader<>(new WebSocketMessageReader(), webSocketRequestParseTimer),
										onError,
										openTelemetry
								)
						))
				));
		server.start();
	}

}
