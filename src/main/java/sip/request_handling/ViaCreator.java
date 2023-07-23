package sip.request_handling;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;

import sip.Address;
import sip.SipSentProtocol;
import sip.Via;
import tcp.server.SocketConnection;

public class ViaCreator {

	public Via createVia(SocketConnection socketConnection) {
		var address = (InetSocketAddress) socketConnection.getAddress();
		return new Via(new SipSentProtocol("SIP", "2.0", "TCP"),
				new Address(address.getHostName(), address.getPort()),
				Map.of("branch", "z9hG4bK25235636")
		);
	}
}
