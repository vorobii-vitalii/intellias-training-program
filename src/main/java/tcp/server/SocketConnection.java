package tcp.server;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

// ISP
// metrics, grafana, assertions in load test

/**
 * The interface should only be used in endpoints
 * Implementation MUST be thread safe
 */
public interface SocketConnection extends BytesStorage, Metadata, Closeable {
	boolean isClosed();
	void changeOperation(OperationType operationType);
	void setProtocol(String protocol);
	void appendResponse(ByteBuffer buffer, Consumer<SocketConnection> onWriteCallback);
}
