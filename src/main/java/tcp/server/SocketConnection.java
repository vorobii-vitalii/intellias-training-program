package tcp.server;

import io.opentelemetry.api.trace.Span;
import util.Serializable;
import util.UnsafeConsumer;

import java.io.InputStream;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.function.Consumer;

// ISP
// metrics, grafana, assertions in load test

/**
 * The interface should only be used in endpoints
 */
public interface SocketConnection {
	boolean isOpen();
	void appendBytesToContext(byte[] data);
	void freeContext();
	InputStream getContextInputStream();
	void setProtocol(String protocol);
	void changeOperation(int operation);
	void appendResponse(Serializable response, EventEmitter eventEmitter, Consumer<SocketConnection> onWriteCallback);
	void appendResponse(ByteBuffer buffer, Consumer<SocketConnection> onWriteCallback);
	void setMetadata(String key, Object value);
	String getMetadata(String key);
	void close();
}
