package tcp.server;

import util.Serializable;

import java.net.SocketAddress;

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
	int getResponsesSize();
	void setMetadata(String key, Object value);
	String getMetadata(String key);
	SocketAddress getAddress();
	void close();
}
