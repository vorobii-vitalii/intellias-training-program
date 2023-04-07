package websocket;

import http.HTTPMethod;
import http.HTTPRequest;
import http.HTTPResponse;
import http.handler.HTTPAcceptOperationHandler;
import http.handler.HTTPReadOperationHandler;
import http.handler.HTTPRequestHandler;
import request_handler.ProcessingRequest;
import request_handler.RequestProcessor;
import tcp.server.ServerAttachment;
import tcp.server.TCPServer;
import tcp.server.TCPServerConfig;
import tcp.server.handler.DelegatingReadOperationHandler;
import tcp.server.handler.WriteOperationHandler;
import util.Constants;
import websocket.endpoint.DocumentStreamingWebSocketEndpoint;
import websocket.endpoint.WebSocketEndpoint;
import websocket.endpoint.WebSocketEndpointProvider;
import websocket.handler.WebSocketChangeProtocolHTTPHandlerStrategy;
import websocket.handler.WebSocketProtocolChanger;
import websocket.handler.WebSocketRequestHandler;

import java.net.StandardProtocolFamily;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;

import static java.nio.channels.SelectionKey.*;


public class HttpServer {
	public static final int QUEUE_SIZE = 1000;
	private static final Integer WS_VERSION = 13;
	private static final int PORT = 8000;
	private static final String HOSTNAME = "127.0.0.1";

	public static void main(String[] args) {
		var blockingQueue = new ArrayBlockingQueue<ProcessingRequest<HTTPRequest, HTTPResponse>>(QUEUE_SIZE);

		WebSocketEndpointProvider webSocketEndpointProvider = new WebSocketEndpointProvider(Map.of(
						"/example", new WebSocketEndpoint() {
							@Override
							public void onConnect(SelectionKey selectionKey) {
								ServerAttachment attachmentObject = (ServerAttachment) selectionKey.attachment();
								System.out.println("New websocket connection " + selectionKey + " object = " + attachmentObject);
							}

							@Override
							public void onMessage(SelectionKey selectionKey, WebSocketMessage message) {
								ServerAttachment attachmentObject = (ServerAttachment) selectionKey.attachment();
								System.out.println("Read websocket message: " + message);
								var messageToWrite = new WebSocketMessage();
								messageToWrite.setFin(true);
								messageToWrite.setOpCode(OpCode.TEXT);
								var replyMessage = new String(message.getPayload()) + " Reply";
								messageToWrite.setPayload(replyMessage.getBytes(StandardCharsets.UTF_8));
								attachmentObject.responses().add(messageToWrite);
								selectionKey.interestOps(OP_WRITE);
							}
						},
						"/documents", new DocumentStreamingWebSocketEndpoint(),
						"/stream", new WebSocketEndpoint() {
							private final Set<SelectionKey> connections = Collections.synchronizedSet(new HashSet<>());

							@Override
							public void onConnect(SelectionKey selectionKey) {
								ServerAttachment attachmentObject = (ServerAttachment) selectionKey.attachment();
								System.out.println("New websocket connection " + selectionKey + " object = " + attachmentObject);
								connections.add(selectionKey);
							}

							@Override
							public void onMessage(SelectionKey selectionKey, WebSocketMessage message) {
								switch (message.getOpCode()) {
									case CONNECTION_CLOSE -> {
										System.out.println("Client request close of connection " + selectionKey);
										connections.remove(selectionKey);
										selectionKey.cancel();
									}
									case TEXT -> {
										System.out.println("New document update: " + message);
										var messageToBroadcast = new WebSocketMessage();
										messageToBroadcast.setFin(true);
										messageToBroadcast.setOpCode(OpCode.TEXT);
										messageToBroadcast.setPayload(message.getPayload());
										for (SelectionKey connection : connections) {
											if (connection != selectionKey) {
												((ServerAttachment) connection.attachment()).responses().add(messageToBroadcast);
												connection.interestOps(OP_WRITE);
											}
										}
										var messageToAuthor = new WebSocketMessage();
										messageToAuthor.setFin(true);
										messageToAuthor.setOpCode(OpCode.TEXT);
										messageToAuthor.setPayload("Change was broadcast-ed".getBytes(StandardCharsets.UTF_8));
										((ServerAttachment) selectionKey.attachment()).responses().add(messageToAuthor);
										selectionKey.interestOps(OP_WRITE);
										selectionKey.selector().wakeup();
									}
								}
							}
						}
		));


		HTTPRequestHandler handler = new HTTPRequestHandler(new ArrayList<>(List.of(new WebSocketChangeProtocolHTTPHandlerStrategy(List.of(
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
										.filter("Upgrade"::equals)
										.isPresent(),
						request -> request.getHeaders()
										.getHeaderValue(Constants.HTTPHeaders.WEBSOCKET_KEY)
										.isPresent(),
						request -> request.getHeaders().getHeaderValue(Constants.HTTPHeaders.WEBSOCKET_VERSION)
										.filter(String.valueOf(WS_VERSION)::equals)
										.isPresent()
		), Set.of(), webSocketEndpointProvider))));

		var requestProcessor = new RequestProcessor<>(
						blockingQueue,
						Executors.newCachedThreadPool(),
						handler
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
										OP_ACCEPT, new HTTPAcceptOperationHandler(),
										OP_READ, new DelegatingReadOperationHandler(Map.of(
														Constants.Protocol.HTTP, new HTTPReadOperationHandler(
																		blockingQueue,
																		List.of(new WebSocketProtocolChanger(webSocketEndpointProvider))
														),
														Constants.Protocol.WEB_SOCKET, new WebSocketRequestHandler(webSocketEndpointProvider)
										)),
										OP_WRITE, new WriteOperationHandler(Map.of(
														Constants.Protocol.HTTP, (selectionKey, msg) -> {
															System.out.println("On message written " + msg + " " + selectionKey);
															ServerAttachment attachment = (ServerAttachment) selectionKey.attachment();
															if (isEmpty(attachment.responses())) {
																selectionKey.interestOps(OP_READ);
															}
														},
														Constants.Protocol.WEB_SOCKET, (selectionKey, msg) -> {
															System.out.println("On message written " + msg + " " + selectionKey);
															ServerAttachment attachment = (ServerAttachment) selectionKey.attachment();
															if (isEmpty(attachment.responses())) {
																selectionKey.interestOps(OP_READ);
															}
														}
										))
						));
		server.start();
	}

	private static boolean isEmpty(Collection<?> collection) {
		return collection == null || collection.isEmpty();
	}

}
