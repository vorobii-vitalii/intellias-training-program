package websocket;

import http.*;
import http.handler.HTTPReadOperationHandler;
import http.handler.HTTPRequestHandler;
import request_handler.ProcessingRequest;
import request_handler.RequestProcessor;
import tcp.server.TCPServer;
import tcp.server.TCPServerConfig;
import tcp.server.handler.DelegatingReadOperationHandler;
import tcp.server.handler.GenericWriteOperationHandler;
import util.Constants;
import http.handler.HTTPAcceptOperationHandler;
import websocket.handler.WebSocketChangeProtocolHTTPHandlerStrategy;
import websocket.handler.WebSocketProtocolChanger;
import websocket.handler.WebSocketRequestHandler;

import java.net.StandardProtocolFamily;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;

import static java.nio.channels.SelectionKey.*;


public class HttpServer {
	private static final int BUFFER_CAPACITY = 1000;
	private static final int PORT = 8000;
	private static final String HOSTNAME = "127.0.0.1";
	public static final int QUEUE_SIZE = 1000;


	public static void main(String[] args) {
		var blockingQueue = new ArrayBlockingQueue<ProcessingRequest<HTTPRequest, HTTPResponse>>(QUEUE_SIZE);

		var requestProcessor = new RequestProcessor<>(
						blockingQueue,
						Executors.newCachedThreadPool(),
						new HTTPRequestHandler(new ArrayList<>(List.of(new WebSocketChangeProtocolHTTPHandlerStrategy())))
		);
		new Thread(requestProcessor).start();

		var server = new TCPServer(
						TCPServerConfig.builder()
										.setHost(HOSTNAME)
										.setPort(PORT)
										.setProtocolFamily(StandardProtocolFamily.INET)
										.build(),
						SelectorProvider.provider(),
						System.err::println,
						Map.of(
										OP_ACCEPT, new HTTPAcceptOperationHandler(BUFFER_CAPACITY),
										OP_READ, new DelegatingReadOperationHandler(Map.of(
														Constants.Protocol.HTTP, new HTTPReadOperationHandler(
																		blockingQueue,
																		List.of(new WebSocketProtocolChanger())
														),
														Constants.Protocol.WEB_SOCKET, new WebSocketRequestHandler()
										)),
										OP_WRITE, new GenericWriteOperationHandler()
						));
		server.start();
	}

}
