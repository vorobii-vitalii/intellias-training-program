package udp;

import tcp.server.BytesSource;

public interface PacketTypeRecognizer {
	String getType(BytesSource bytesSource);
}
