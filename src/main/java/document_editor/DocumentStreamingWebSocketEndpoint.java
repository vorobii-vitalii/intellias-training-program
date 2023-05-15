package document_editor;

import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SelectionKey;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import document_editor.event.DisconnectEvent;
import document_editor.event.EditEvent;
import document_editor.event.Event;
import document_editor.event.NewConnectionEvent;
import tcp.server.CompositeInputStream;
import tcp.server.SocketConnection;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;
import websocket.endpoint.WebSocketEndpoint;

public class DocumentStreamingWebSocketEndpoint implements WebSocketEndpoint {
	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentStreamingWebSocketEndpoint.class);

	private final BlockingQueue<Event> eventsQueue;
	private final ObjectMapper objectMapper;

	public DocumentStreamingWebSocketEndpoint(
			BlockingQueue<Event> eventsQueue,
			ObjectMapper objectMapper
	) {
		this.eventsQueue = eventsQueue;
		this.objectMapper = objectMapper;
	}

	@Override
	public void onConnect(SocketConnection socketConnection) {
		LOGGER.info("New websocket connection {}", socketConnection);
	}

	public enum ResponseType {
		ON_CONNECT,
		ADD,
		ADD_BULK,
		CHANGES, DELETE
	}

	public enum RequestType {
		CONNECT,
		CHANGES
	}

	public record Response(ResponseType responseType, Object payload) {
	}

	public record ConnectDocumentReply(int connectionId) {
	}

	public record TreePathEntry(boolean a, int b) {

	}

	public record Change(List<TreePathEntry> a, Character b) {

	}

	public record Request(RequestType type, List<Change> payload) {
	}

	@Override
	public void onMessage(SocketConnection socketConnection, WebSocketMessage message) {
		switch (message.getOpCode()) {
			case CONNECTION_CLOSE -> {
				var webSocketMessage = new WebSocketMessage();
				webSocketMessage.setFin(true);
				webSocketMessage.setOpCode(OpCode.CONNECTION_CLOSE);
				webSocketMessage.setPayload(new byte[] {});
				// TODO: Close TCP
				socketConnection.appendResponse(webSocketMessage, null, null, SelectionKey::cancel);
				socketConnection.changeOperation(OP_WRITE);
				eventsQueue.add(new DisconnectEvent(socketConnection));
			}
			case TEXT, CONTINUATION, BINARY -> {
				LOGGER.debug("Handling message... bytes in context {}", socketConnection.getContextLength());
				var payload = message.getPayload();
				if (message.isFin()) {
//					var totalPayloadSize = payload.length + socketConnection.getContextLength();
					var compositeInputStream = new CompositeInputStream(
							socketConnection.getContextInputStream(),
							new ByteArrayInputStream(payload)
					);
					try {
						int totalPayloadSize = socketConnection.getContextLength() + payload.length;

						var request = objectMapper.readValue(new InputStream() {
							private int index = 0;

							@Override
							public int read() {
								if (index == totalPayloadSize) {
									return -1;
								}
								if (index < socketConnection.getContextLength()) {
									return socketConnection.getByteFromContext(index++) & 0xff;
								}
								return payload[(index++) - socketConnection.getContextLength()] & 0xff;
							}

							@Override
							public void close() {

							}
						}, Request.class);
						onMessage(socketConnection, request);
						socketConnection.freeContext();
					} catch (Exception e) {
						LOGGER.error("Error = ", e);
					}

				} else {
					socketConnection.appendBytesToContext(payload);
				}
			}
		}
	}

	private void onMessage(SocketConnection socketConnection, Request request) throws InterruptedException {
		if (request.type == RequestType.CONNECT) {
			eventsQueue.put(new NewConnectionEvent(socketConnection));
		} else if (request.type == RequestType.CHANGES) {
			eventsQueue.put(new EditEvent(request.payload));
		}
	}
}
