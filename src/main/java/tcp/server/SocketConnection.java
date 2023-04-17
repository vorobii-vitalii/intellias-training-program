package tcp.server;

import util.Serializable;

import java.net.SocketAddress;

public interface SocketConnection {
	void setProtocol(String protocol);
	void changeOperation(int operation);
	void appendResponse(Serializable response);
	void setMetadata(String key, Object value);

	String getMetadata(String key);

	SocketAddress getAddress();
	void close();
}
