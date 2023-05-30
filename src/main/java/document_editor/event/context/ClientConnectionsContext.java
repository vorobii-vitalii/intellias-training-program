package document_editor.event.context;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jcip.annotations.NotThreadSafe;
import tcp.MessageSerializer;
import tcp.server.BufferCopier;
import tcp.server.ByteBufferPool;
import tcp.server.OperationType;
import tcp.server.SocketConnection;
import util.Serializable;

@NotThreadSafe
public class ClientConnectionsContext {
	private static final Logger LOGGER = LoggerFactory.getLogger(ClientConnectionsContext.class);
	private final Map<SocketConnection, Instant> connectionsMap = new HashMap<>();
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
		connectionsMap.entrySet().parallelStream()
				.forEach(e -> {
					if (isConnected(e.getValue())) {
						var connection = e.getKey();
						LOGGER.info("Sending message to {}", connection);
						try {
							connection.appendResponse(bufferCopier.copy(buffer), r -> {});
							connection.changeOperation(OperationType.WRITE);
						} catch (Exception error) {
							error.printStackTrace();
						}
					}
				});
	}

	public void removeDisconnectedClients() {
		var connectionsToRemove = new HashSet<SocketConnection>();
		for (var entry : connectionsMap.entrySet()) {
			if (!isConnected(entry.getValue())) {
				try {
					var connection = entry.getKey();
					connectionsToRemove.add(connection);
					connection.close();
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
