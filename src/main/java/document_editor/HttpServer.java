package document_editor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import document_editor.event.DisconnectEvent;
import document_editor.event.handler.DisconnectEventHandler;
import document_editor.event.Event;
import document_editor.event.EventContext;
import document_editor.event.GetMetricsEvent;
import document_editor.event.handler.EditEventHandler;
import document_editor.event.handler.GetMetricsEventHandler;
import document_editor.event.handler.MessageDistributeEventHandler;
import document_editor.event.handler.NewConnectionEventHandler;
import document_editor.mongo.MongoReactiveAtomBuffer;
import http.domain.HTTPMethod;
import http.domain.HTTPRequest;
import http.handler.FileDownloadHTTPHandlerStrategy;
import http.handler.HTTPAcceptOperationHandler;
import http.handler.HTTPRequestHandler;
import http.post_processor.HTTPResponsePostProcessor;
import http.post_processor.ProtocolChangerHTTPResponsePostProcessor;
import http.reader.HTTPRequestMessageReader;

import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.bson.Document;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
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
import token_bucket.RefillProcess;
import token_bucket.TokenBucket;
import util.Constants;
import util.UnsafeConsumer;
import websocket.domain.WebSocketMessage;
import websocket.endpoint.WebSocketEndpointProvider;
import websocket.handler.WebSocketChangeProtocolHTTPHandlerStrategy;
import websocket.handler.WebSocketProtocolChanger;
import websocket.handler.WebSocketRequestHandler;
import websocket.reader.WebSocketMessageReader;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static java.nio.channels.SelectionKey.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class HttpServer {
	public static final int QUEUE_SIZE = 200_000;
	public static final String PROMETHEUS_ENDPOINT = "/prometheus";
	public static final int MAX_TOKENS_WRITE = 1000;
	public static final int MAX_TOKENS_READ = 10;
	public static final int SELECTION_TIMEOUT = 1;
	private static final AtomicInteger atomicInteger = new AtomicInteger(1);
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpServer.class);
	private static final Integer WS_VERSION = 13;
	private static final int DOCUMENT_ID = 13;
	private static final ScheduledExecutorService metricsExecutor = Executors.newSingleThreadScheduledExecutor();
	private static final ScheduledExecutorService documentEventsExecutor = Executors.newSingleThreadScheduledExecutor();
	private static final ScheduledExecutorService queuesSizeReporter = Executors.newSingleThreadScheduledExecutor();
	private static final ScheduledExecutorService refillExecutor = Executors.newSingleThreadScheduledExecutor();

	private static String getMongoConnectionURL() {
		return Optional.ofNullable(System.getenv("MONGO_URL"))
				.orElse("mongodb://localhost:27017");
	}

	private static int getPort() {
		return Optional.ofNullable(System.getenv("PORT"))
				.map(Integer::parseInt)
				.orElse(8000);
	}

	private static String getHost() {
		return Optional.ofNullable(System.getenv("HOST"))
				.orElse("127.0.0.1");
	}

	private static MongoCollection<Document> getBlockingCollection() {
		ConnectionString connString = new ConnectionString(getMongoConnectionURL());
		ServerApi serverApi = ServerApi.builder()
				.version(ServerApiVersion.V1)
				.build();
		MongoClientSettings settings = MongoClientSettings.builder()
				.applyConnectionString(connString)
				.serverApi(serverApi)
				.applyToConnectionPoolSettings(builder -> builder.minSize(10).maxSize(150))
				.build();
		MongoClient mongoClient = MongoClients.create(settings);
		MongoDatabase mongoClientDatabase = mongoClient.getDatabase("test");
		return mongoClientDatabase.getCollection("docCol");
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
				.merge(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "logical-service-name")));

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

		var httpRequestQueueGauge = Gauge.builder("http.request.queue.size", httpRequestQueue::size)
				.register(prometheusRegistry);
		var webSocketRequestQueueGauge = Gauge.builder("websocket.request.queue.size", webSocketRequestQueue::size)
				.register(prometheusRegistry);
		var eventsQueueGauge = Gauge.builder("events.queue.size", webSocketRequestQueue::size)
				.register(prometheusRegistry);

		queuesSizeReporter.scheduleWithFixedDelay(() -> {
			httpRequestQueueGauge.measure();
			webSocketRequestQueueGauge.measure();
			eventsQueueGauge.measure();
		}, 5, 5, SECONDS);

		var blockingCollection = getBlockingCollection();
		blockingCollection.createIndex(new Document().append("documentId", 1).append("path", 1));
		blockingCollection.createIndex(new Document().append("documentId", 1).append("deleting", 1));
		final MongoReactiveAtomBuffer mongoReactiveAtomBuffer = new MongoReactiveAtomBuffer(blockingCollection, DOCUMENT_ID, objectMapper);
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

		var handler = new HTTPRequestHandler(
				List.of(
						webSocketChangeProtocolHTTPHandlerStrategy,
						new FileDownloadHTTPHandlerStrategy(p -> p.isEmpty() || p.equals("/"), "index.html", "text/html"),
						new PrometheusMetricsHTTPRequestHandler(prometheusRegistry, PROMETHEUS_ENDPOINT)
				),
				httpResponsePostProcessors,
				openTelemetry
		);

		var httpRequestProcessor = new NetworkRequestProcessor<>(httpRequestQueue, handler, httpRequestProcessingTimer, httpRequestsCount);
		var webSocketRequestProcessor = new NetworkRequestProcessor<>(
				webSocketRequestQueue,
				new WebSocketRequestHandler(webSocketEndpointProvider),
				webSocketRequestProcessingTimer,
				webSocketRequestsCount
		);
		var eventContext = new EventContext();
		var connectionsGauge = Gauge.builder("document.connections.count", eventContext::connections).register(prometheusRegistry);
		var notWrittenResponsesGauge =
				Gauge.builder("document.unwritten.responses", eventContext::numberNotWrittenResponses).register(prometheusRegistry);

		metricsExecutor.scheduleWithFixedDelay(() -> eventsQueue.offer(new GetMetricsEvent()), 5, 5, SECONDS);

		refillExecutor.scheduleWithFixedDelay(new RefillProcess<>(tokenBuckets), 1, 1, SECONDS);

		startProcess(httpRequestProcessor, "HTTP Request processor");
		documentEventsExecutor.scheduleWithFixedDelay(new DocumentMessageEventsHandler(eventsQueue, eventContext, List.of(
				new DisconnectEventHandler(),
				new NewConnectionEventHandler(mongoReactiveAtomBuffer, atomicInteger::getAndIncrement, objectMapper, dbReadDocumentTimer),
				new MessageDistributeEventHandler(),
				new GetMetricsEventHandler(connectionsGauge, notWrittenResponsesGauge),
				new EditEventHandler(mongoReactiveAtomBuffer, changesApplyTimer)
		)), 250, 250, MILLISECONDS);
		startProcess(webSocketRequestProcessor, "WebSocket Request processor");
		startProcess(new DocumentChangeWatcher(blockingCollection, eventsQueue, objectMapper), "Document change watcher");

		Consumer<SelectionKey> closeConnection = selectionKey -> {
			var socketConnection = new ConnectionImpl(selectionKey);
			LOGGER.info("Closing connection {}", socketConnection);
			tokenBuckets.remove(((ServerAttachment) selectionKey.attachment()).getWriteTokenBucket());
			tokenBuckets.remove(((ServerAttachment) selectionKey.attachment()).getReadTokenBucket());
			socketConnection.close();
			eventsQueue.offer(new DisconnectEvent(socketConnection));
		};

		BiConsumer<SelectionKey, Throwable> onError = (selectionKey, throwable) -> {
			var socketConnection = new ConnectionImpl(selectionKey);
			LOGGER.info("Closing connection {} because of", socketConnection, throwable);
			tokenBuckets.remove(((ServerAttachment) selectionKey.attachment()).getWriteTokenBucket());
			tokenBuckets.remove(((ServerAttachment) selectionKey.attachment()).getReadTokenBucket());
			socketConnection.close();
			eventsQueue.offer(new DisconnectEvent(socketConnection));
		};


		var readOperationHandler = new DelegatingReadOperationHandler(Map.of(
				Constants.Protocol.HTTP, new GenericReadOperationHandler<>(
						httpRequestQueue,
						new SocketMessageReader<>(
								new HTTPRequestMessageReader((name, val) -> Collections.singletonList(val.trim())),
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
		));
		var byteBufferPool = new ByteBufferPool(ByteBuffer::allocateDirect);

		Selector[] wsSelectors = createSelectors(5);
		Selector[] httpSelectors = createSelectors(2);

		RoundRobinProvider<Selector> webSocketSelectorProvider = new RoundRobinProvider<>(wsSelectors);

		UnsafeConsumer<SelectionKey> changeSelector = selectionKey -> {
			var attachment = selectionKey.attachment();
			var channel = (SocketChannel) selectionKey.channel();
			var newSelector = webSocketSelectorProvider.get();
			channel.register(newSelector, OP_READ, attachment);
			selectionKey.cancel();
		};
		var writeOperationHandlerHTTP = new WriteOperationHandler(
				messagesWriteTimer,
				messagesWrittenCounter,
				onError,
				byteBufferPool,
				openTelemetry, changeSelector);

		var writeOperationHandlerWebSockets = new WriteOperationHandler(
				messagesWriteTimer,
				messagesWrittenCounter,
				onError,
				byteBufferPool,
				openTelemetry, selectionKey -> selectionKey.interestOps(OP_READ));

		Map<Integer, Consumer<SelectionKey>> operationHandlerByTypeHTTP =
				Map.of(OP_READ, new GenericReadOperationHandler<>(
						httpRequestQueue,
						new SocketMessageReader<>(
								new HTTPRequestMessageReader((name, val) -> Collections.singletonList(val.trim())),
								httpRequestParseTimer
						),
						onError,
						openTelemetry
				), OP_WRITE, writeOperationHandlerHTTP);
		Map<Integer, Consumer<SelectionKey>> operationHandlerByTypeWebSockets =
				Map.of(
						OP_READ, new GenericReadOperationHandler<>(
								webSocketRequestQueue,
								new SocketMessageReader<>(new WebSocketMessageReader(), webSocketRequestParseTimer),
								onError,
								openTelemetry
						),
						OP_WRITE, writeOperationHandlerWebSockets);

		for (int i = 0; i < wsSelectors.length; i++) {
			startProcess(new Poller(wsSelectors[i], operationHandlerByTypeWebSockets, closeConnection, SELECTION_TIMEOUT), "WebSocket selector " + i);
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
								openTelemetry
						)
				));
		server.start();
	}

}
