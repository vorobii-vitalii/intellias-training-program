package http.handler;

import http.HTTPRequest;

import java.nio.channels.SelectionKey;

public interface ProtocolChanger {
	void changeProtocol(ProtocolChangeContext protocolChangeContext);
	String getProtocolName();
}
