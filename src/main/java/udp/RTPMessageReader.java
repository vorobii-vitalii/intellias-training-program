package udp;

import javax.annotation.Nullable;

import tcp.server.BytesSource;
import tcp.server.EventEmitter;
import tcp.server.reader.MessageReader;
import tcp.server.reader.exception.ParseException;
import util.Pair;

public class RTPMessageReader implements MessageReader<RTPMessage> {
	private static final int VERSION_BITMASK = 0b11000000;
	private static final int PADDED_BITMASK = 0b00100000;
	private static final int EXTENSION_ENABLED_BITMASK = 0b00010000;
	private static final int CONTRIBUTION_SOURCES_COUNT_BITMASK = 0b00001111;
	private static final int MARKER_BITMASK = 0b10000000;
	private static final int PAYLOAD_TYPE_BITMASK = 0b01111111;
	private static final int EXPECTED_MESSAGE_SIZE_WITHOUT_CONTRIBUTION_SOURCES = 12;

	@Nullable
	@Override
	public Pair<RTPMessage, Integer> read(BytesSource bytesSource, EventEmitter eventEmitter) throws ParseException {
		int numBytes = bytesSource.size();
		if (numBytes < EXPECTED_MESSAGE_SIZE_WITHOUT_CONTRIBUTION_SOURCES) {
			throw new ParseException("Message length < EXPECTED_MESSAGE_SIZE_WITHOUT_CONTRIBUTION_SOURCES = 12");
		}
		var firstByte = bytesSource.get(0);
		var contributionSourcesCount = (firstByte & CONTRIBUTION_SOURCES_COUNT_BITMASK);
		var expectedMessageSizeWithContributionSources = calcExpectedMessageSizeWithContributionSources(contributionSourcesCount);
		if (numBytes < expectedMessageSizeWithContributionSources) {
			throw new ParseException("Message length < EXPECTED_MESSAGE_SIZE_WITHOUT_CONTRIBUTION_SOURCES + (" + contributionSourcesCount + ")");
		}
		var version = (firstByte & VERSION_BITMASK) >> 6;
		var isExtensionEnabled = (firstByte & EXTENSION_ENABLED_BITMASK) != 0;
		var isPadded = (firstByte & PADDED_BITMASK) != 0;
		var secondByte = bytesSource.get(1);
		var isMarker = (secondByte & MARKER_BITMASK) != 0;
		var payloadType = (secondByte & PAYLOAD_TYPE_BITMASK);
		var sequenceNumber = constructInt(bytesSource.get(2), bytesSource.get(3));
		var timestamp = constructInt(bytesSource.get(4), bytesSource.get(5), bytesSource.get(6), bytesSource.get(7));
		var synchronizationSource = constructInt(bytesSource.get(8), bytesSource.get(9), bytesSource.get(10), bytesSource.get(11));
		var contributionSourceIdentifiers = new int[contributionSourcesCount];
		for (int i = 0, m = 12; i < contributionSourcesCount; i++, m += 4) {
			contributionSourceIdentifiers[i] =
					constructInt(bytesSource.get(m), bytesSource.get(m + 1), bytesSource.get(m + 2), bytesSource.get(m + 3));
		}
		var rtpMessage = new RTPMessage();
		rtpMessage.setVersion(version);
		rtpMessage.setPadded(isPadded);
		rtpMessage.setExtensionEnabled(isExtensionEnabled);
		rtpMessage.setMarker(isMarker);
		rtpMessage.setPayloadType(payloadType);
		rtpMessage.setSequenceNumber(sequenceNumber);
		rtpMessage.setTimestamp(timestamp);
		rtpMessage.setSynchronizationSource(synchronizationSource);
		rtpMessage.setContributionSourceIdentifiers(contributionSourceIdentifiers);
		rtpMessage.setPayload(bytesSource.extract(expectedMessageSizeWithContributionSources, numBytes));
		return new Pair<>(rtpMessage, numBytes);
	}

	private static int calcExpectedMessageSizeWithContributionSources(int contributionSourcesCount) {
		return EXPECTED_MESSAGE_SIZE_WITHOUT_CONTRIBUTION_SOURCES + contributionSourcesCount * 4;
	}

	private int constructInt(byte b1, byte b2) {
		return b2 + (b1 << 8);
	}

	private int constructInt(byte b1, byte b2, byte b3, byte b4) {
		return b4 + (b3 << 8) + (b2 << 16) + (b1 << 24);
	}

}
