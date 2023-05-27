package http.reader;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import http.domain.HTTPHeaders;
import http.domain.HTTPRequest;
import http.domain.HTTPRequestLine;
import tcp.CharSequenceImpl;
import tcp.server.BufferContext;
import tcp.server.EventEmitter;
import tcp.server.reader.MessageReader;
import tcp.server.reader.exception.ParseException;
import util.ByteUtils;
import util.Constants;
import util.Pair;

public class HTTPRequestMessageReader implements MessageReader<HTTPRequest> {
	private static final byte CARRIAGE_RETURN = '\r';
	private static final byte LINE_FEED = '\n';
	private static final char HEADER_DELIMITER = ':';
	private static final int NOT_FOUND = -1;
	public static final int CLRF_LENGTH = 2;

	private final BiFunction<CharSequence, CharSequence, List<String>> headerValuesExtractor;

	public HTTPRequestMessageReader(BiFunction<CharSequence, CharSequence, List<String>> headerValuesExtractor) {
		this.headerValuesExtractor = headerValuesExtractor;
	}

	@Override
	public Pair<HTTPRequest, Integer> read(BufferContext bufferContext, EventEmitter eventEmitter) throws ParseException {
		if (!isProbableRequest(bufferContext)) {
			return null;
		}
		int prevCLRFIndex = -CLRF_LENGTH;
		var n = bufferContext.size();
		var i = 0;
		HTTPRequestLine requestLine = null;
		var httpHeaders = new HTTPHeaders();
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
					requestLine = HTTPRequestLine.parse(line);
					eventEmitter.emit("Parsed request line");
				}
				else {
					var headerDelimiterIndex = find(line, HEADER_DELIMITER);
					eventEmitter.emit("Found delimiter");
					if (headerDelimiterIndex == NOT_FOUND) {
						throw new ParseException("HTTP header key-value pair should be delimiter-ed by : character " + line);
					}
					var headerName = line.subSequence(0, headerDelimiterIndex);
					eventEmitter.emit("Extracted header name");
					var headerValue = line.subSequence(headerDelimiterIndex + 1, line.length());
					eventEmitter.emit("Extracted value");
					httpHeaders.addSingleHeader(headerName.toString(), headerValue.toString());
					eventEmitter.emit("Parsed header");
				}
				prevCLRFIndex = i;
				i++;
			}
		}
		int payloadSize = httpHeaders
				.getHeaderValue(Constants.HTTPHeaders.CONTENT_LENGTH)
				.map(Integer::parseInt)
				.orElse(0);

		int bodyStartIndex = i + CLRF_LENGTH;
		int readPayloadBytes = bufferContext.size() - bodyStartIndex;
		if (readPayloadBytes < payloadSize) {
			return null;
		}
		var body = payloadSize == 0 ? new byte[0] : bufferContext.extract(bodyStartIndex, bodyStartIndex + payloadSize);
		return new Pair<>(new HTTPRequest(requestLine, httpHeaders, body), bodyStartIndex + payloadSize);
	}

	private boolean isProbableRequest(BufferContext bufferContext) {
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

	private int find(CharSequence charSequence, char b) {
		var n = charSequence.length();
		for (int i = 0; i < n; i++) {
			if (charSequence.charAt(i) == b) {
				return i;
			}
		}
		return -1;
	}

}
