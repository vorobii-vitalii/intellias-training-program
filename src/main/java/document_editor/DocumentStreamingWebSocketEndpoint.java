package document_editor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import document_editor.event.DisconnectEvent;
import document_editor.event.Event;
import document_editor.event.NewConnectionEvent;
import document_editor.mongo.MongoReactiveAtomBuffer;
import tcp.server.SocketConnection;
import websocket.domain.WebSocketMessage;
import websocket.endpoint.WebSocketEndpoint;

public class DocumentStreamingWebSocketEndpoint implements WebSocketEndpoint {
	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentStreamingWebSocketEndpoint.class);

	private final BlockingQueue<Event> eventsQueue;
	private final MongoReactiveAtomBuffer mongoReactiveAtomBuffer;
	private final ObjectMapper objectMapper;

	public DocumentStreamingWebSocketEndpoint(
			BlockingQueue<Event> eventsQueue,
			MongoReactiveAtomBuffer mongoReactiveAtomBuffer,
			ObjectMapper objectMapper
	) {
		this.eventsQueue = eventsQueue;
		this.mongoReactiveAtomBuffer = mongoReactiveAtomBuffer;
		this.objectMapper = objectMapper;
	}

	@Override
	public void onConnect(SocketConnection socketConnection) {
		LOGGER.info("New websocket connection {}", socketConnection);
	}

	public enum ResponseType {
		ON_CONNECT,
		ADD,
		DELETE
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

	private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

	@Override
	public void onMessage(SocketConnection socketConnection, WebSocketMessage message) {
		switch (message.getOpCode()) {
			case CONNECTION_CLOSE -> {
				socketConnection.close();
				try {
					eventsQueue.put(new DisconnectEvent(socketConnection));
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			case TEXT, CONTINUATION, BINARY -> {
				LOGGER.info("Handling message... bytes in context {}", socketConnection.getContextLength());
				var payload = message.getPayload();
				if (message.isFin()) {
					// To avoid additional memory allocation
					var totalPayloadSize = payload.length + socketConnection.getContextLength();
					try {
						Request request = objectMapper.readValue(new InputStream() {
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

						socketConnection.freeContext();
//						LOGGER.info("Message {}", request);
						executorService.execute(() -> onMessage(socketConnection, request));
						socketConnection.freeContext();
						//						LOGGER.info("Message {}", request);
//						executorService.execute(() -> onMessage(socketConnection, value));
					} catch (IOException e) {
						LOGGER.error("error = ", e);
					}

				} else {
//					LOGGER.info("Appending {} to context", Arrays.toString(payload));
					socketConnection.appendBytesToContext(payload);
				}
			}
		}
	}

	private void onMessage(SocketConnection socketConnection, Request request) {
		if (request.type == RequestType.CONNECT) {
			try {
				eventsQueue.put(new NewConnectionEvent(socketConnection));
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		} else if (request.type == RequestType.CHANGES) {
			mongoReactiveAtomBuffer.applyChangesBulk(request.payload());
		}
	}
}
