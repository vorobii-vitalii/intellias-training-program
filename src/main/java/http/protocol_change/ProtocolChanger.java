package http.protocol_change;

public interface ProtocolChanger {
	void changeProtocol(ProtocolChangeContext protocolChangeContext);
	String getProtocolName();
}
