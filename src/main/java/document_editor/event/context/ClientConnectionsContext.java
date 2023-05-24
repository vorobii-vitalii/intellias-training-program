package document_editor.event.context;

import java.nio.channels.SelectionKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jcip.annotations.NotThreadSafe;
import tcp.server.SocketConnection;
import util.Serializable;

@NotThreadSafe
public class ClientConnectionsContext {
	private final Map<SocketConnection, Instant> connectionsMap = new HashMap<>();
	private final int maxWaitMs;
	private final Supplier<Instant> currentTimeProvider;

	public ClientConnectionsContext(int maxWaitMs, Supplier<Instant> currentTimeProvider) {
		this.maxWaitMs = maxWaitMs;
		this.currentTimeProvider = currentTimeProvider;
	}

	public void addOrUpdateConnection(SocketConnection connection) {
		connectionsMap.put(connection, currentTimeProvider.get());
	}

	public void broadCastMessage(Serializable message) {
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
		var connectionsToRemove = new HashSet<SocketConnection>();
		for (var entry : connectionsMap.entrySet()) {
			if (!isConnected(entry.getValue())) {
				try {
					var connection = entry.getKey();
					connection.close();
					connectionsToRemove.add(connection);
				}
				catch (Exception error) {
					error.printStackTrace();
				}
			}
		}
		connectionsToRemove.forEach(connectionsMap::remove);
	}

	private boolean isConnected(Instant instant) {
		return (currentTimeProvider.get().toEpochMilli() - instant.toEpochMilli()) <= maxWaitMs;
	}

}
