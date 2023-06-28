package sip;

import tcp.CharSequenceImpl;
import tcp.server.BufferContext;
import tcp.server.EventEmitter;
import tcp.server.reader.MessageReader;
import tcp.server.reader.exception.ParseException;
import util.Pair;

import javax.annotation.Nullable;

public class SipMessageReader implements MessageReader<SipRequest> {
	private static final byte CARRIAGE_RETURN = '\r';
	private static final byte LINE_FEED = '\n';
	private static final char HEADER_DELIMITER = ':';
	private static final int NOT_FOUND = -1;
	private static final int CLRF_LENGTH = 2;

	@Nullable
	@Override
	public Pair<SipRequest, Integer> read(BufferContext bufferContext, EventEmitter eventEmitter) throws ParseException {
		int prevCLRFIndex = -CLRF_LENGTH;
		var n = bufferContext.size();
		var i = 0;
		SipRequestLine requestLine = null;
		var sipHeaders = new SipHeaders();
		for (; i < n - 1; i++) {
			// Detect CLRF
			var b = bufferContext.get(i);
			if (b == CARRIAGE_RETURN && bufferContext.get(i + 1) == LINE_FEED) {
				// Last header
				if (prevCLRFIndex + 2 == i) {
					break;
				}
				var line = new CharSequenceImpl(bufferContext, prevCLRFIndex + CLRF_LENGTH, i);
				eventEmitter.emit("Extracted line");
				if (requestLine == null) {
					requestLine = SipRequestLine.parse(line.toString());
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
					sipHeaders.addSingleHeader(headerName, headerValue.toString());
					eventEmitter.emit("Parsed header");
				}
				prevCLRFIndex = i;
				i++;
			}
		}
		int payloadSize = sipHeaders.getContentLength();

		int bodyStartIndex = i + CLRF_LENGTH;
		int readPayloadBytes = bufferContext.size() - bodyStartIndex;
		if (readPayloadBytes < payloadSize) {
			return null;
		}
		var body = payloadSize == 0 ? new byte[0] : bufferContext.extract(bodyStartIndex, bodyStartIndex + payloadSize);
		return new Pair<>(new SipRequest(requestLine, sipHeaders, body), bodyStartIndex + payloadSize);
	}

}
