package document_editor.event.context;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jcip.annotations.NotThreadSafe;
import tcp.MessageSerializer;
import tcp.server.BufferCopier;
import tcp.server.OperationType;
import tcp.server.SocketConnection;
import util.Serializable;

@NotThreadSafe
public class ClientConnectionsContext {
	private static final Logger LOGGER = LoggerFactory.getLogger(ClientConnectionsContext.class);
	private final Map<SocketConnection, Instant> connectionsMap = new ConcurrentHashMap<>();
	private final int maxWaitMs;
	private final Supplier<Instant> currentTimeProvider;
	private final MessageSerializer messageSerializer;
	private final BufferCopier bufferCopier;

	public ClientConnectionsContext(
			int maxWaitMs,
			Supplier<Instant> currentTimeProvider,
			MessageSerializer messageSerializer,
			BufferCopier bufferCopier
	) {
		this.maxWaitMs = maxWaitMs;
		this.currentTimeProvider = currentTimeProvider;
		this.messageSerializer = messageSerializer;
		this.bufferCopier = bufferCopier;
	}

	public void addOrUpdateConnection(SocketConnection connection) {
		connectionsMap.put(connection, currentTimeProvider.get());
	}

	public void broadCastMessage(Serializable message) {
		var buffer = messageSerializer.serialize(message, e -> {
		});
		doForConnectedClients(connection -> {
			connection.appendResponse(bufferCopier.copy(buffer), r -> {});
			connection.changeOperation(OperationType.WRITE);
		});
	}

	private void disconnectClients(HashSet<SocketConnection> connectionsToRemove) {
		connectionsToRemove.forEach(c -> {
			c.close();
			connectionsMap.remove(c);
		});
	}

	public void removeDisconnectedClients() {
		doForConnectedClients(connection -> {});
	}

	private void doForConnectedClients(Consumer<SocketConnection> connectionConsumer) {
		var connectionsToRemove = new HashSet<SocketConnection>();
		for (Map.Entry<SocketConnection, Instant> e : connectionsMap.entrySet()) {
			var connection = e.getKey();
			if (isConnected(e.getValue(), connection)) {
				try {
					connectionConsumer.accept(connection);
				} catch (Exception error) {
					error.printStackTrace();
					connectionsToRemove.add(connection);
				}
			} else {
				connectionsToRemove.add(connection);
			}
		}
		disconnectClients(connectionsToRemove);
	}

	private boolean isConnected(Instant instant, SocketConnection connection) {
		return !connection.isClosed() && (currentTimeProvider.get().toEpochMilli() - instant.toEpochMilli()) <= maxWaitMs;
	}

}
