package stun;

import javax.annotation.Nullable;

import tcp.server.BytesSource;
import tcp.server.EventEmitter;
import tcp.server.reader.MessageReader;
import tcp.server.reader.exception.ParseException;
import util.Pair;

public class StunMessageReader implements MessageReader<StunMessage> {

	public static final int STUN_HEADERS_LENGTH = 20;

	@Nullable
	@Override
	public Pair<StunMessage, Integer> read(BytesSource bytesSource, EventEmitter eventEmitter) throws ParseException {
		var numBytes = bytesSource.size();
		if (numBytes < STUN_HEADERS_LENGTH || !isStunMessage(bytesSource.get(0))) {
			return null;
		}
		int messageType = constructInt(bytesSource.get(0), bytesSource.get(1));
		int messageLength = constructInt(bytesSource.get(2), bytesSource.get(3));
		int magicCookie = constructInt(bytesSource.get(4), bytesSource.get(5), bytesSource.get(6), bytesSource.get(7));
		var transactionId = bytesSource.extract(8, STUN_HEADERS_LENGTH);
		if ((numBytes - STUN_HEADERS_LENGTH) < messageLength) {
			return null;
		}
		var payload = bytesSource.extract(STUN_HEADERS_LENGTH, numBytes);
		return new Pair<>(
				new StunMessage(messageType, messageLength, magicCookie, transactionId, payload),
				numBytes
		);
	}

	private boolean isStunMessage(byte b) {
		return ((b & (1 << 7)) == 0) && ((b & (1 << 6)) == 0);
	}

	private int constructInt(byte b1, byte b2) {
		return b2 + (b1 << 8);
	}

	private int constructInt(byte b1, byte b2, byte b3, byte b4) {
		return b4 + (b3 << 8) + (b2 << 16) + (b1 << 24);
	}

}
