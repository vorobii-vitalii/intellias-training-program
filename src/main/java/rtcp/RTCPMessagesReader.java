package rtcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tcp.server.BytesSource;
import tcp.server.EventEmitter;
import tcp.server.reader.MessageReader;
import tcp.server.reader.exception.ParseException;
import util.Pair;

/**
 * RTCP messages reader
 * Each UDP datagram can contain multiple RTCP "reports", however it might be that MTU is lower than total size of all reports, that's why
 * receiver of reports should parse as many as it can, and drop others
 */
public class RTCPMessagesReader implements MessageReader<List<RTCPMessage>> {
	private static final Logger LOGGER = LoggerFactory.getLogger(RTCPMessagesReader.class);
	private static final int PACKET_HEADERS_SIZE = 4;
	private static final byte VERSION_BITMASK = (byte) 0b11000000;
	private static final byte IS_PADDED_BITMASK = (byte) 0b00100000;
	private static final byte METADATA_BITMASK = (byte) 0b00011111;

	private final Map<Byte, RTCPReportParser> reportParserByPayloadType;

	public RTCPMessagesReader(List<RTCPReportParser> rtcpReportParsers) {
		this.reportParserByPayloadType = rtcpReportParsers.stream()
				.collect(Collectors.toMap(RTCPReportParser::getHandledReportType, v -> v));
	}

	@Nullable
	@Override
	public Pair<List<RTCPMessage>, Integer> read(BytesSource bytesSource, EventEmitter eventEmitter) throws ParseException {
		int numBytes = bytesSource.size();
		var rtcpMessages = new ArrayList<RTCPMessage>();
		for (var i = 0; i < numBytes; ) {
			var bytesLeft = numBytes - i;
			if (bytesLeft < PACKET_HEADERS_SIZE) {
				break;
			}
			// TODO: Implement STUN
			var length = (constructInt(bytesSource.get(i + 2), bytesSource.get(i + 3)) + 1) * 4;
			if (bytesLeft < length) {
				LOGGER.warn("Not enough bytes report length =  {}, i = {}, numBytes = {}", length, i, numBytes);
				break;
			}
			var firstByte = bytesSource.get(i);
			var version = (firstByte & VERSION_BITMASK) >> 6;
			var isPadded = (firstByte & IS_PADDED_BITMASK) != 0;
			var metadata = (byte) (firstByte & METADATA_BITMASK);
			var payloadType = bytesSource.get(i + 1);
			var parser = reportParserByPayloadType.get(payloadType);
			var headers = new RTCPHeaders(version, isPadded, length);
			LOGGER.info("Parser RTCP headers {}", headers);
			if (parser != null) {
				var rtcpReport = parser.parse(
						bytesSource.extract(i + PACKET_HEADERS_SIZE, i + length),
						isPadded,
						metadata
				);
				rtcpMessages.add(new RTCPMessage(headers, rtcpReport));
			}
			i += length;
		}
		return new Pair<>(rtcpMessages, numBytes);
	}

	private int constructInt(byte b1, byte b2) {
		return b2 + (b1 << 8);
	}

	private int constructInt(byte b1, byte b2, byte b3, byte b4) {
		return b4 + (b3 << 8) + (b2 << 16) + (b1 << 24);
	}

}
