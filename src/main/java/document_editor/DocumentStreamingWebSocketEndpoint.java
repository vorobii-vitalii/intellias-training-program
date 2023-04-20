package document_editor;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.treedoc.buffer.AtomBuffer;
import org.treedoc.path.MutableTreeDocPath;
import org.treedoc.path.MutableTreeDocPathImpl;
import org.treedoc.path.TreeDocPath;
import org.treedoc.utils.Pair;
import tcp.server.SocketConnection;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;
import websocket.endpoint.WebSocketEndpoint;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.nio.channels.SelectionKey.OP_WRITE;

public class DocumentStreamingWebSocketEndpoint implements WebSocketEndpoint {
	private static final Logger LOGGER = LogManager.getLogger(DocumentStreamingWebSocketEndpoint.class);

	private final Set<SocketConnection> socketConnections = new HashSet<>();

	private final AtomBuffer<Character, Integer> atomBuffer;
	private final AtomicInteger id = new AtomicInteger();

	public DocumentStreamingWebSocketEndpoint(AtomBuffer<Character, Integer> atomBuffer) {
		this.atomBuffer = atomBuffer;
	}

	@Override
	public void onConnect(SocketConnection socketConnection) {
		LOGGER.info("New websocket connection {}", socketConnection);
	}

	enum ResponseType {
		ON_CONNECT,
		ADD,
		DELETE
	}

	enum RequestType {
		CONNECT,
		DELETE,
		ADD
	}

	private record Response(ResponseType responseType, Object payload) {
	}

	private record ConnectDocumentReply(
					Integer connectionId,
					List<Pair<List<Pair<Boolean, Integer>>, Character>> currentState
	) {
	}

	private record Request(RequestType type, Object payload) {

		public <T> T getTypedPayload(TypeToken<T> typeToken) {
			Gson gson = new Gson();
			String json = gson.toJson(payload);
			return gson.fromJson(json, typeToken.getType());
		}

	}

	private <T extends Comparable<T>> List<Pair<Boolean, T>> toPairs(TreeDocPath<T> path) {
		var pairs = new ArrayList<Pair<Boolean, T>>(path.length());
		for (var i = 0; i < path.length(); i++) {
			pairs.add(new Pair<>(path.isSet(i), path.disambiguatorAt(i)));
		}
		return pairs;
	}

	private <T extends Comparable<T>> TreeDocPath<T> toPath(List<Pair<Boolean, T>> list) {
		MutableTreeDocPath<T> treeDocPath = new MutableTreeDocPathImpl<>(list.size());
		for (var i = 0; i < list.size(); i++) {
			treeDocPath.disambiguatorAt(i, list.get(i).second());
			if (list.get(i).first()) {
				treeDocPath.set(i);
			}
		}
		return treeDocPath;
	}

	@Override
	public void onMessage(SocketConnection socketConnection, WebSocketMessage message) {
		switch (message.getOpCode()) {
			case CONNECTION_CLOSE -> {
				LOGGER.info("Client request close of connection {}", socketConnection);
				socketConnections.remove(socketConnection);
				socketConnection.close();
			}
			case TEXT -> {
				var payload = message.getPayload();
				if (message.isFin()) {
					// To avoid additional memory allocation
					var totalPayloadSize = payload.length + socketConnection.getContextLength();
					var totalPayload = socketConnection.getContextLength() > 0 ? new byte[totalPayloadSize] : payload;
					if (socketConnection.getContextLength() > 0) {
						for (var i = 0; i < socketConnection.getContextLength(); i++) {
							totalPayload[i] = socketConnection.getByteFromContext(i);
						}
						System.arraycopy(payload, 0, totalPayload,
								socketConnection.getContextLength(), payload.length);
						socketConnection.freeContext();
					}
					var messageText = new String(totalPayload, StandardCharsets.UTF_8);
					var request = new Gson().fromJson(messageText, Request.class);
					LOGGER.info("New text message {} from connection {}", request, socketConnection);
					onMessage(socketConnection, request);
				}
				else {
					socketConnection.appendBytesToContext(payload);
				}
			}
		}
	}

	private void onMessage(SocketConnection socketConnection, Request request) {
		if (request.type == RequestType.CONNECT) {
			var webSocketMessage = new WebSocketMessage();
			webSocketMessage.setFin(true);
			List<Pair<TreeDocPath<Integer>, Character>> entries = atomBuffer.getEntries();
			String s1 = new Gson().toJson(new Response(
							ResponseType.ON_CONNECT,
							new ConnectDocumentReply(id.incrementAndGet(), entries.stream()
											.map(s -> new Pair<>(toPairs(s.first()), s.second()))
											.collect(Collectors.toList())
							))
			);
			webSocketMessage.setPayload(s1.getBytes(StandardCharsets.UTF_8));
			webSocketMessage.setOpCode(OpCode.TEXT);
			socketConnection.appendResponse(webSocketMessage);
			socketConnection.changeOperation(OP_WRITE);
			socketConnections.add(socketConnection);
		}
		else if (request.type == RequestType.DELETE) {
			List<Pair<Boolean, Integer>> list = request.getTypedPayload(new TypeToken<>() {
			});
			atomBuffer.delete(toPath(list));
			var webSocketMessage = new WebSocketMessage();
			webSocketMessage.setFin(true);
			webSocketMessage.setPayload(new Gson()
							.toJson(new Response(ResponseType.DELETE, list))
							.getBytes(StandardCharsets.UTF_8));
			webSocketMessage.setOpCode(OpCode.TEXT);
			for (SocketConnection connection : socketConnections) {
				if (!connection.equals(socketConnection)) {
					connection.appendResponse(webSocketMessage);
					connection.changeOperation(OP_WRITE);
				}
			}
		}
		else if (request.type == RequestType.ADD) {
			Pair<List<Pair<Boolean, Integer>>, Character> pair = request.getTypedPayload(new TypeToken<>() {
			});
			atomBuffer.insert(toPath(pair.first()), pair.second());
			var webSocketMessage = new WebSocketMessage();
			webSocketMessage.setFin(true);
			webSocketMessage.setPayload(new Gson()
							.toJson(new Response(ResponseType.ADD, pair))
							.getBytes(StandardCharsets.UTF_8));
			webSocketMessage.setOpCode(OpCode.TEXT);
			for (SocketConnection connection : socketConnections) {
				if (!connection.equals(socketConnection)) {
					connection.appendResponse(webSocketMessage);
					connection.changeOperation(OP_WRITE);
				}
			}
		}
	}
}
