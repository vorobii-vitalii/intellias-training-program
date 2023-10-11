package sip.request_handling;

import sip.Address;
import tcp.server.ServerAttachment;
import tcp.server.SocketConnection;

public class SocketConnectionProvider {
	private final TCPConnectionsContext tcpConnectionsContext;

	public SocketConnectionProvider(TCPConnectionsContext tcpConnectionsContext) {
		this.tcpConnectionsContext = tcpConnectionsContext;
	}

	public SocketConnection getSocketConnection(Address address) {
		return ((ServerAttachment) tcpConnectionsContext.establishConnection(address)).toSocketConnection();
	}

}
