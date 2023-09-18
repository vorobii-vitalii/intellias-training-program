package sip;

import tcp.server.impl.CharSequenceImpl;
import tcp.server.BytesSource;
import tcp.server.EventEmitter;
import tcp.server.reader.MessageReader;
import tcp.server.reader.exception.ParseException;
import util.Pair;

import javax.annotation.Nullable;

public class SipMessageReader implements MessageReader<SipMessage> {
	private static final byte CARRIAGE_RETURN = '\r';
	private static final byte LINE_FEED = '\n';
	private static final char HEADER_DELIMITER = ':';
	private static final int NOT_FOUND = -1;
	private static final int CLRF_LENGTH = 2;

	@Nullable
	@Override
	public Pair<SipMessage, Integer> read(BytesSource bytesSource, EventEmitter eventEmitter) throws ParseException {
		if (!isProbableRequest(bytesSource)) {
			return null;
		}
		// Trailing CRLF
		if (startsWithCRLF(bytesSource)) {
			return new Pair<>(null, 4);
		}
		SipMessageBuilder sipMessageBuilder = null;
		int prevCLRFIndex = -CLRF_LENGTH;
		var n = bytesSource.size();
		var i = 0;
		for (; i < n - 1; i++) {
			// Detect CLRF
			var b = bytesSource.get(i);
			if (b == CARRIAGE_RETURN && bytesSource.get(i + 1) == LINE_FEED) {
				// Last header
				if (prevCLRFIndex + 2 == i) {
					break;
				}
				var line = new CharSequenceImpl(bytesSource, prevCLRFIndex + CLRF_LENGTH, i);
				eventEmitter.emit("Extracted line");
				if (sipMessageBuilder == null) {
					final String lineStr = line.toString();
					if (SipResponseLine.isSipResponseLine(lineStr)) {
						sipMessageBuilder = new SipResponseBuilder(SipResponseLine.parse(lineStr));
					} else {
						sipMessageBuilder = new SipRequestBuilder(SipRequestLine.parse(lineStr));
					}
					eventEmitter.emit("Parsed request line");
				}
				else {
					var headerDelimiterIndex = ParseUtils.findFromFromBegging(line, HEADER_DELIMITER);
					eventEmitter.emit("Found delimiter");
					if (headerDelimiterIndex == NOT_FOUND) {
						throw new ParseException("SIP header key-value pair should be delimiter-ed by : character " + line);
					}
					var headerName = line.subSequence(0, headerDelimiterIndex).toString().trim();
					eventEmitter.emit("Extracted header name");
					var headerValue = line.subSequence(headerDelimiterIndex + 1, line.length());
					eventEmitter.emit("Extracted value");
					sipMessageBuilder.setHeader(headerName, headerValue.toString());
					eventEmitter.emit("Parsed header");
				}
				prevCLRFIndex = i;
				i++;
			}
		}
		if (sipMessageBuilder == null) {
			throw new IllegalStateException("Not possible");
		}
		int payloadSize = sipMessageBuilder.getContentLength();

		int bodyStartIndex = i + CLRF_LENGTH;
		int readPayloadBytes = bytesSource.size() - bodyStartIndex;
		if (readPayloadBytes < payloadSize) {
			return null;
		}
		var body = payloadSize == 0 ? new byte[0] : bytesSource.extract(bodyStartIndex, bodyStartIndex + payloadSize);
		sipMessageBuilder.setBody(body);
		return new Pair<>(sipMessageBuilder.build(), bodyStartIndex + payloadSize);
	}

	private boolean startsWithCRLF(BytesSource bufferContext) {
		if (bufferContext.size() < 4) {
			return false;
		}
		return bufferContext.get(0) == CARRIAGE_RETURN
				&& bufferContext.get(1) == LINE_FEED
				&& bufferContext.get(2) == CARRIAGE_RETURN
				&& bufferContext.get(3) == LINE_FEED;
	}

	private boolean isProbableRequest(BytesSource bufferContext) {
		int n = bufferContext.size();
		for (int i = 0; i < n - 3; i++) {
			if (bufferContext.get(i) == CARRIAGE_RETURN
					&& bufferContext.get(i + 1) == LINE_FEED
					&& bufferContext.get(i + 2) == CARRIAGE_RETURN
					&& bufferContext.get(i + 3) == LINE_FEED) {
				return true;
			}
		}
		return false;
	}

}
