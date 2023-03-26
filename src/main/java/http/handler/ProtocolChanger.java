package http.handler;

import java.nio.channels.SelectionKey;

public interface ProtocolChanger {
	void changeProtocol(SelectionKey selectionKey);
	String getProtocolName();
}
