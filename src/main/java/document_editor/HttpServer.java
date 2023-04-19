package document_editor;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import http.domain.HTTPMethod;
import http.domain.HTTPRequest;
import http.handler.*;
import http.post_processor.HTTPResponsePostProcessor;
import http.post_processor.ProtocolChangerHTTPResponsePostProcessor;
import http.reader.HTTPRequestMessageReader;
import document_editor.mongo.MongoDBAtomBuffer;
import org.apache.log4j.BasicConfigurator;
import request_handler.NetworkRequest;
import request_handler.NetworkRequestProcessor;
import tcp.server.SocketMessageReader;
import tcp.server.TCPServer;
import tcp.server.TCPServerConfig;
import tcp.server.handler.DelegatingReadOperationHandler;
import tcp.server.handler.GenericReadOperationHandler;
import tcp.server.handler.WriteOperationHandler;
import util.Constants;
import websocket.domain.WebSocketMessage;
import websocket.endpoint.WebSocketEndpointProvider;
import http.handler.FileDownloadHTTPHandlerStrategy;
import websocket.handler.WebSocketChangeProtocolHTTPHandlerStrategy;
import websocket.handler.WebSocketProtocolChanger;
import websocket.handler.WebSocketRequestHandler;
import websocket.reader.WebSocketMessageReader;

import java.net.StandardProtocolFamily;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

import static java.nio.channels.SelectionKey.*;


public class HttpServer {
	public static final int QUEUE_SIZE = 1000;
	private static final Integer WS_VERSION = 13;
	private static final int PORT = 8000;
	private static final String HOSTNAME = "127.0.0.1";

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

	public static void main(String[] args) {
		BasicConfigurator.configure();
		var httpRequestQueue = new ArrayBlockingQueue<NetworkRequest<HTTPRequest>>(QUEUE_SIZE);
		var webSocketRequestQueue = new ArrayBlockingQueue<NetworkRequest<WebSocketMessage>>(QUEUE_SIZE);

		ConnectionString connString = new ConnectionString(getMongoConnectionURL());
		ServerApi serverApi = ServerApi.builder()
						.version(ServerApiVersion.V1)
						.build();
		MongoClientSettings settings = MongoClientSettings.builder()
						.applyConnectionString(connString)
						.serverApi(serverApi)
						.build();
		MongoClient mongoClient = MongoClients.create(settings);

		MongoDatabase mongoClientDatabase = mongoClient.getDatabase("test");

//		var driver = GraphDatabase.driver("bolt://0.0.0.0:7687", AuthTokens.basic("neo4j", "password"));

		WebSocketEndpointProvider webSocketEndpointProvider = new WebSocketEndpointProvider(Map.of(
						"/documents", new DocumentStreamingWebSocketEndpoint(new MongoDBAtomBuffer(mongoClientDatabase.getCollection("docCol"), 13))
		));


		WebSocketChangeProtocolHTTPHandlerStrategy webSocketChangeProtocolHTTPHandlerStrategy = new WebSocketChangeProtocolHTTPHandlerStrategy(List.of(
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


		HTTPRequestHandler handler = new HTTPRequestHandler(
						List.of(
										webSocketChangeProtocolHTTPHandlerStrategy,
										new FileDownloadHTTPHandlerStrategy(p -> p.isEmpty() || p.equals("/"), "index.html", "text/html")
						),
						httpResponsePostProcessors
		);

		var httpRequestProcessor = new NetworkRequestProcessor<>(httpRequestQueue, handler);
		var webSocketRequestProcessor = new NetworkRequestProcessor<>(
						webSocketRequestQueue,
						new WebSocketRequestHandler(webSocketEndpointProvider)
		);
		new Thread(httpRequestProcessor).start();
		new Thread(webSocketRequestProcessor).start();

		var server = new TCPServer(
						TCPServerConfig.builder()
										.setHost(getHost())
										.setPort(getPort())
										.setProtocolFamily(StandardProtocolFamily.INET)
										.build(),
						SelectorProvider.provider(),
						System.err::println,
						Map.of(
										OP_ACCEPT, new HTTPAcceptOperationHandler(),
										OP_READ, new DelegatingReadOperationHandler(Map.of(
														Constants.Protocol.HTTP, new GenericReadOperationHandler<>(
																		httpRequestQueue,
																		new SocketMessageReader<>(new HTTPRequestMessageReader((name, val) -> Collections.singletonList(val.trim())))
														),
														Constants.Protocol.WEB_SOCKET, new GenericReadOperationHandler<>(
																		webSocketRequestQueue,
																		new SocketMessageReader<>(new WebSocketMessageReader())
														)
										)),
										OP_WRITE, new WriteOperationHandler()
						));
		server.start();
	}

}
