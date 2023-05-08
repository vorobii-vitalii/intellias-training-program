package tcp.server;

import io.opentelemetry.api.trace.Span;
import util.Serializable;

import java.net.SocketAddress;
import java.nio.channels.Selector;

// ISP
// metrics, grafana, assertions in load test
public interface SocketConnection {
	void appendBytesToContext(byte[] data);
	void freeContext();
	int getContextLength();
	byte getByteFromContext(int index);
	void setProtocol(String protocol);
	void changeOperation(int operation);
	void appendResponse(Serializable response);
	void appendResponse(Serializable response, Span parentSpan);
	int getResponsesSize();
	void setMetadata(String key, Object value);
	String getMetadata(String key);
	SocketAddress getAddress();
	void close();
}
