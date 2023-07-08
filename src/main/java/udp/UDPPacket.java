package udp;

import java.net.SocketAddress;

public record UDPPacket<T>(T payload, SocketAddress sender) {
}
