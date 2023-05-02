package document_editor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.bson.Document;
import org.treedoc.path.MutableTreeDocPath;
import org.treedoc.path.MutableTreeDocPathImpl;
import org.treedoc.path.TreeDocPath;
import org.treedoc.utils.Pair;

import document_editor.event.Event;
import document_editor.event.MessageDistributeEvent;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;

// NGINX reconnect, Envoy proxy, EBPF
// connection stickiness
public class DocumentChangeWatcher implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentChangeWatcher.class);
	public static final int BATCH_SIZE = 100;

	private final MongoCollection<Document> mongoCollection;
	private final BlockingQueue<Event> eventQueue;
	private final ObjectMapper objectMapper;

	public DocumentChangeWatcher(MongoCollection<Document> mongoCollection, BlockingQueue<Event> eventQueue, ObjectMapper objectMapper) {
		this.mongoCollection = mongoCollection;
		this.eventQueue = eventQueue;
		this.objectMapper = objectMapper;
	}

	@Override
	public void run() {
		try (var cursor = mongoCollection.watch()
				.fullDocument(FullDocument.UPDATE_LOOKUP)
				.batchSize(BATCH_SIZE)
				.cursor()
		) {
			while (cursor.hasNext()) {
				final ChangeStreamDocument<Document> documentChangeStreamDocument = cursor.next();
				var document = documentChangeStreamDocument.getFullDocument();
				//						LOGGER.info("New change {}", documentChangeStreamDocument);
				if (document == null) {
					continue;
				}
				var webSocketMessage = new WebSocketMessage();
				webSocketMessage.setFin(true);
				webSocketMessage.setOpCode(OpCode.BINARY);

				// Delete event
				if (document.containsKey("deleting")) {
//					webSocketMessage.setPayload(
//							new Gson()
//									.toJson(new DocumentStreamingWebSocketEndpoint.Response(
//											DocumentStreamingWebSocketEndpoint.ResponseType.DELETE, toPairs(fromString(document.getString("path")))))
//									.getBytes(StandardCharsets.UTF_8)
//					);
					webSocketMessage.setPayload(objectMapper.writeValueAsBytes(new DocumentStreamingWebSocketEndpoint.Response(
							DocumentStreamingWebSocketEndpoint.ResponseType.DELETE, fromString(document.getString("path")))));
				}
				// insert event
				else {
//					webSocketMessage.setPayload(new Gson()
//							.toJson(new DocumentStreamingWebSocketEndpoint.Response(
//									DocumentStreamingWebSocketEndpoint.ResponseType.ADD,
//									new Pair<>(toPairs(fromString(document.getString("path"))), document.getString("value").charAt(0))))
//							.getBytes(StandardCharsets.UTF_8));
					webSocketMessage.setPayload(objectMapper.writeValueAsBytes(new DocumentStreamingWebSocketEndpoint.Response(
							DocumentStreamingWebSocketEndpoint.ResponseType.ADD,
							new PairDTO<>(fromString(document.getString("path")), document.getString("value").charAt(0)))));
				}
				try {
					eventQueue.put(new MessageDistributeEvent(webSocketMessage));
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	private List<DocumentStreamingWebSocketEndpoint.TreePathEntry> fromString(String str) {
		try {
			return objectMapper.readValue(Base64.getDecoder().decode(str),
					new TypeReference<>() {
					});
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

//	private TreeDocPath<Integer> fromString(String str) {
//		String[] arr = str.split(" ");
//		MutableTreeDocPath<Integer> path = new MutableTreeDocPathImpl<>(arr.length);
//		for (int i = 0; i < arr.length; i++) {
//			String[] s = arr[i].split(",");
//			if (s[0].charAt(0) == '1') {
//				path.set(i);
//			}
//			path.disambiguatorAt(i, Integer.parseInt(s[1]));
//		}
//		return path;
//	}

	private <T extends Comparable<T>> List<PairDTO<Boolean, T>> toPairs(TreeDocPath<T> path) {
		var pairs = new ArrayList<PairDTO<Boolean, T>>(path.length());
		for (var i = 0; i < path.length(); i++) {
			pairs.add(new PairDTO<>(path.isSet(i), path.disambiguatorAt(i)));
		}
		return pairs;
	}

}
