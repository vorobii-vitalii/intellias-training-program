package document_editor.event.context;

import java.nio.channels.SelectionKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.jcip.annotations.NotThreadSafe;
import tcp.server.SocketConnection;
import util.Serializable;

@NotThreadSafe
public class EventContext {
	private final Map<SocketConnection, Instant> connectionsMap = new HashMap<>();
	private final int maxWaitMs;

	public EventContext(int maxWaitMs) {
		this.maxWaitMs = maxWaitMs;
	}

	public void addOrUpdateConnection(SocketConnection connection) {
		connectionsMap.put(connection, Instant.now());
	}

	public void broadCastMessage(Serializable message) throws InterruptedException {
		for (var entry : connectionsMap.entrySet()) {
			if (isConnected(entry.getValue())) {
				var connection = entry.getKey();
				try {
					connection.appendResponse(message);
					connection.changeOperation(SelectionKey.OP_WRITE);
				} catch (Exception ignored) {
				}
			}
		}
	}

	public void removeDisconnectedClients() {
		Set<SocketConnection> connectionsToRemove = new HashSet<>();
		for (var entry : connectionsMap.entrySet()) {
			if (!isConnected(entry.getValue())) {
				try {
					var connection = entry.getKey();
					connection.close();
					connectionsToRemove.add(connection);
				}
				catch (Exception error) {

				}
			}
		}
		connectionsToRemove.forEach(connectionsMap::remove);
	}

	private boolean isConnected(Instant instant) {
		return (Instant.now().toEpochMilli() - instant.toEpochMilli()) <= maxWaitMs;
	}

}
