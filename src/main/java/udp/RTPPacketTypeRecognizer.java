package udp;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tcp.server.BytesSource;

public class
RTPPacketTypeRecognizer implements PacketTypeRecognizer {
	private static final Logger LOGGER = LoggerFactory.getLogger(RTPPacketTypeRecognizer.class);

	private static final int FIRST_BIT = (1 << 7);
	private static final int SECOND_BIT = (1 << 6);

	@Override
	public String getType(BytesSource bytesSource) {
		return isRTP(bytesSource) ? "RTP/AVP" : null;
	}

	private boolean isRTP(BytesSource bytesSource) {
		return !bytesSource.isEmpty() && startsWithVersion2(bytesSource);
	}

	private boolean startsWithVersion2(BytesSource bytesSource) {
		var firstByte = bytesSource.get(0);
		LOGGER.info("First byte = {}", firstByte);
		return (firstByte & FIRST_BIT) != 0 && (firstByte & SECOND_BIT) == 0;
	}

}
