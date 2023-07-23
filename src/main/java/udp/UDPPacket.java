package udp;

import java.net.SocketAddress;

public record UDPPacket<T>(T payload, SocketAddress sender) {
	@Override
	public String toString() {
		return "Packet[sender = " + sender + ", payload = " + payload + "]";
	}
}
