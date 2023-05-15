package tcp.server;

import io.opentelemetry.api.trace.Span;
import util.Serializable;
import util.UnsafeConsumer;

import java.io.InputStream;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.util.function.Consumer;

// ISP
// metrics, grafana, assertions in load test
public interface SocketConnection {
	void appendBytesToContext(byte[] data);

	void freeContext();

	int getContextLength();

	byte getByteFromContext(int index);

	InputStream getContextInputStream();

	void setProtocol(String protocol);

	void changeOperation(int operation);

	void appendResponse(Serializable response);

	void appendResponse(Serializable response, Span parentSpan, Span requestSpan, UnsafeConsumer<SelectionKey> onWriteResponseCallback);

	int getResponsesSize();

	void setMetadata(String key, Object value);

	String getMetadata(String key);

	SocketAddress getAddress();

	void close();
}
