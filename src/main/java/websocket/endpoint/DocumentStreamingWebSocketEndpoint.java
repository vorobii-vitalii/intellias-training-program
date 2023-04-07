package websocket.endpoint;

import tcp.server.ServerAttachment;
import websocket.OpCode;
import websocket.WebSocketMessage;

import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.nio.channels.SelectionKey.OP_WRITE;

public class DocumentStreamingWebSocketEndpoint implements WebSocketEndpoint {
	private final Map<SelectionKey, Integer> documentIdByConnection = new ConcurrentHashMap<>();
	private final Map<Integer, List<String>> changesByDocumentId = new ConcurrentHashMap<>();
	private final Map<Integer, Set<SelectionKey>> changesListeners = new ConcurrentHashMap<>();
	private final Map<Integer, ReadWriteLock> lockByDocumentId = new ConcurrentHashMap<>();

	@Override
	public void onConnect(SelectionKey selectionKey) {
		ServerAttachment attachmentObject = (ServerAttachment) selectionKey.attachment();
		System.out.println("New websocket connection " + selectionKey + " object = " + attachmentObject);
	}

	@Override
	public void onMessage(SelectionKey selectionKey, WebSocketMessage message) {
		System.out.println("OnMessage() " + message + " from " + selectionKey);
		switch (message.getOpCode()) {
			case CONNECTION_CLOSE -> {
				System.out.println("Client request close of connection " + selectionKey);
				documentIdByConnection.remove(selectionKey);
				selectionKey.cancel();
			}
			case TEXT -> {
				String messageText = new String(message.getPayload(), StandardCharsets.UTF_8);
				if (messageText.startsWith("CONNECT")) {
					System.out.println("Adding new document listener");
					if (!documentIdByConnection.containsKey(selectionKey)) {
						var documentId = Integer.parseInt(messageText.split(" ")[1]);
						var lock = lockByDocumentId.computeIfAbsent(documentId, k -> new ReentrantReadWriteLock());
						lock.writeLock().lock();
						try {
							documentIdByConnection.put(selectionKey, documentId);
							changesListeners.computeIfAbsent(documentId, k -> new HashSet<>())
											.add(selectionKey);
							var persistedChanges = changesByDocumentId.computeIfAbsent(
											documentId,
											k -> new LinkedList<>()
							);
							if (!persistedChanges.isEmpty()) {
								selectionKey.interestOps(OP_WRITE);
								for (String change : persistedChanges) {
									var messageToBroadcast = new WebSocketMessage();
									messageToBroadcast.setFin(true);
									messageToBroadcast.setOpCode(OpCode.TEXT);
									messageToBroadcast.setPayload(change.getBytes(StandardCharsets.UTF_8));
									((ServerAttachment) selectionKey.attachment()).responses().add(messageToBroadcast);
								}
							}
						}
						finally {
							lock.writeLock().unlock();
						}
					}
				}
				else {
					var documentId = documentIdByConnection.get(selectionKey);
					var lock = lockByDocumentId.computeIfAbsent(documentId, k -> new ReentrantReadWriteLock());
					lock.readLock().lock();

					try {
						var messageToBroadcast = new WebSocketMessage();
						messageToBroadcast.setFin(true);
						messageToBroadcast.setOpCode(OpCode.TEXT);
						messageToBroadcast.setPayload(messageText.getBytes(StandardCharsets.UTF_8));
						if (documentId != null) {
							changesByDocumentId.get(documentId).add(messageText);
							for (var listener : changesListeners.get(documentId)) {
								if (listener != selectionKey) {
									((ServerAttachment) listener.attachment()).responses().add(messageToBroadcast);
									listener.interestOps(OP_WRITE);
								}
							}
						}
					}
					finally {
						lock.readLock().unlock();
					}
				}
				selectionKey.selector().wakeup();
			}
		}
	}
}
