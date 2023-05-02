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
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import monitoring.PrometheusMetricsHTTPRequestHandler;
import request_handler.NetworkRequest;
import request_handler.NetworkRequestProcessor;
import tcp.server.ConnectionImpl;
import tcp.server.SocketMessageReader;
import tcp.server.TCPServer;
import tcp.server.TCPServerConfig;
import tcp.server.handler.DelegatingReadOperationHandler;
import tcp.server.handler.GenericReadOperationHandler;
import tcp.server.handler.WriteOperationHandler;
import util.Constants;
import websocket.domain.WebSocketMessage;
import websocket.endpoint.WebSocketEndpointProvider;
import websocket.handler.WebSocketChangeProtocolHTTPHandlerStrategy;
import websocket.handler.WebSocketProtocolChanger;
import websocket.handler.WebSocketRequestHandler;
import websocket.reader.WebSocketMessageReader;

import java.net.StandardProtocolFamily;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;
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
	private static final AtomicInteger atomicInteger = new AtomicInteger(1);
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpServer.class);
	public static final int QUEUE_SIZE = 200_000;
	private static final Integer WS_VERSION = 13;
	private static final int DOCUMENT_ID = 13;
	public static final String PROMETHEUS_ENDPOINT = "/prometheus";

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
				.applyToConnectionPoolSettings(builder -> {
					builder.minSize(10).maxSize(150);
				})
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

	private static final ScheduledExecutorService metricsExecutor = Executors.newSingleThreadScheduledExecutor();
	private static final ScheduledExecutorService documentEventsExecutor = Executors.newSingleThreadScheduledExecutor();
	private static final ScheduledExecutorService queuesSizeReporter = Executors.newSingleThreadScheduledExecutor();

	public static void main(String[] args) {
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
				"/documents", new DocumentStreamingWebSocketEndpoint(
						eventsQueue,
						mongoReactiveAtomBuffer, objectMapper)
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




		new JvmMemoryMetrics().bindTo(prometheusRegistry);
		new JvmGcMetrics().bindTo(prometheusRegistry);
		new JvmThreadMetrics().bindTo(prometheusRegistry);


		var httpRequestParseTimer = Timer.builder("http.request.parse.time")
				.description("Time to parse HTTP request message")
				.register(prometheusRegistry);


		var webSocketRequestParseTimer = Timer.builder("websocket.request.parse.time")
				.description("Time to parse WebSocket request message")
				.register(prometheusRegistry);

		var successReadsCounter = Counter.builder("request.success.reads").register(prometheusRegistry);
		var failureReadsCounter = Counter.builder("request.failure.reads").register(prometheusRegistry);

		var messagesWriteTimer = Timer.builder("messages.write.time")
				.description("Time to write into socket ")
				.register(prometheusRegistry);

		var httpRequestProcessingTimer = Timer.builder("http.request.processing")
				.description("Time to process single HTTP request")
				.register(prometheusRegistry);

		var messagesWrittenCounter = Counter.builder("messages.written").description("Number of written messages").register(prometheusRegistry);

		var webSocketRequestProcessingTimer = Timer.builder("websocket.request.processing")
				.description("Time to process single WebSocket request")
				.register(prometheusRegistry);

		var httpRequestsCount = Counter.builder("http.requests.count").register(prometheusRegistry);
		var webSocketRequestsCount = Counter.builder("websocket.requests.count").register(prometheusRegistry);


		HTTPRequestHandler handler = new HTTPRequestHandler(
				List.of(
						webSocketChangeProtocolHTTPHandlerStrategy,
						new FileDownloadHTTPHandlerStrategy(p -> p.isEmpty() || p.equals("/"), "index.html", "text/html"),
						new FileDownloadHTTPHandlerStrategy(p -> p.equals("/static/domain_pb.js"), "domain_pb.js", "application/javascript"),
						new PrometheusMetricsHTTPRequestHandler(prometheusRegistry, PROMETHEUS_ENDPOINT)
				),
				httpResponsePostProcessors
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

		startProcess(httpRequestProcessor, "HTTP Request processor");
		documentEventsExecutor.scheduleWithFixedDelay(new DocumentMessageEventsHandler(eventsQueue, eventContext, List.of(
				new DisconnectEventHandler(),
				new NewConnectionEventHandler(mongoReactiveAtomBuffer, atomicInteger::getAndIncrement, objectMapper),
				new MessageDistributeEventHandler(),
				new GetMetricsEventHandler(connectionsGauge, notWrittenResponsesGauge)
		)), 250, 250, MILLISECONDS);
		startProcess(webSocketRequestProcessor, "WebSocket Request processor");
		startProcess(new DocumentChangeWatcher(blockingCollection, eventsQueue, objectMapper), "Document change watcher");

		Consumer<SelectionKey> closeConnection = selectionKey -> {
			var socketConnection = new ConnectionImpl(selectionKey);
			LOGGER.info("Closing connection {}", socketConnection);
			socketConnection.close();
			eventsQueue.offer(new DisconnectEvent(socketConnection));
		};

		BiConsumer<SelectionKey, Throwable> onError = (selectionKey, throwable) -> {
			var socketConnection = new ConnectionImpl(selectionKey);
			LOGGER.info("Closing connection {} because of", socketConnection, throwable);
			socketConnection.close();
			eventsQueue.offer(new DisconnectEvent(socketConnection));
		};

		var server = new TCPServer(
				TCPServerConfig.builder()
						.setHost(getHost())
						.setPort(getPort())
						.setProtocolFamily(StandardProtocolFamily.INET)
						.onConnectionClose(closeConnection)
						.build(),
				SelectorProvider.provider(),
				System.err::println,
				Map.of(
						OP_ACCEPT, new HTTPAcceptOperationHandler(),
						OP_READ, new DelegatingReadOperationHandler(Map.of(
								Constants.Protocol.HTTP, new GenericReadOperationHandler<>(
										httpRequestQueue,
										new SocketMessageReader<>(
												new HTTPRequestMessageReader((name, val) -> Collections.singletonList(val.trim())),
												httpRequestParseTimer, successReadsCounter, failureReadsCounter),
										onError
								),
								Constants.Protocol.WEB_SOCKET, new GenericReadOperationHandler<>(
										webSocketRequestQueue,
										new SocketMessageReader<>(new WebSocketMessageReader(), webSocketRequestParseTimer,
												successReadsCounter, failureReadsCounter),
										onError
								)
						)),
						OP_WRITE, new WriteOperationHandler(messagesWriteTimer, messagesWrittenCounter, onError)
				));
		server.start();
	}

}
