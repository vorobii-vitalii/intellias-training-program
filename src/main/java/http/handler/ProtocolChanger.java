package http.handler;

public interface ProtocolChanger {
	void changeProtocol(ProtocolChangeContext protocolChangeContext);
	String getProtocolName();
}
