package http.reader;

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
	private static final byte[] CLRF_BYTES = new byte[]{CARRIAGE_RETURN, LINE_FEED};
	private static final char HEADER_DELIMITER = ':';
	private static final int NOT_FOUND = -1;

	private final BiFunction<CharSequence, CharSequence, List<String>> headerValuesExtractor;

	public HTTPRequestMessageReader(BiFunction<CharSequence, CharSequence, List<String>> headerValuesExtractor) {
		this.headerValuesExtractor = headerValuesExtractor;
	}

	@Override
	public Pair<HTTPRequest, Integer> read(BufferContext bufferContext, EventEmitter eventEmitter) throws ParseException {
		var indexes = ByteUtils.findAllMatches(bufferContext, CLRF_BYTES);
		eventEmitter.emit("Found all matching CLRF indexes");
		var headerEndIndex = ByteUtils.getIndexOfRepetitiveSubsequence(indexes, CLRF_BYTES.length);
		eventEmitter.emit("Found header end index");
		if (headerEndIndex == NOT_FOUND) {
			return null;
		}
		var requestLine = HTTPRequestLine.parse(extractSubsequence(bufferContext, indexes, 0).toString());
		eventEmitter.emit("Parsed request line");
		var httpHeaders = new HTTPHeaders();
		for (var i = 1; i < indexes.size() - 1; i++) {
			var line = extractSubsequence(bufferContext, indexes, i);
			var headerDelimiterIndex = find(line, HEADER_DELIMITER);
			if (headerDelimiterIndex == NOT_FOUND) {
				throw new ParseException("HTTP header key-value pair should be delimiter-ed by : character " + line);
			}
			var headerName = line.subSequence(0, headerDelimiterIndex);
			var headerValues = headerValuesExtractor.apply(headerName, line.subSequence(headerDelimiterIndex + 1, line.length()));
			httpHeaders.addHeaders(headerName.toString(), headerValues);
		}
		eventEmitter.emit("Parser headers");
		int payloadSize = httpHeaders
				.getHeaderValue(Constants.HTTPHeaders.CONTENT_LENGTH)
				.map(Integer::parseInt)
				.orElse(0);

		int bodyStartIndex = indexes.get(headerEndIndex) + CLRF_BYTES.length;
		int readPayloadBytes = bufferContext.size() - bodyStartIndex;
		if (readPayloadBytes < payloadSize) {
			return null;
		}
		var body = new byte[payloadSize];
		for (var i = 0; i < payloadSize; i++) {
			body[i] = bufferContext.get(bodyStartIndex + i);
		}
		eventEmitter.emit("Parsed body");
		return new Pair<>(new HTTPRequest(requestLine, httpHeaders, body), bodyStartIndex + payloadSize);
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

	private CharSequence extractSubsequence(BufferContext context, List<Integer> indexes, int subsequenceNum) {
		int start = subsequenceNum == 0 ? 0 : indexes.get(subsequenceNum - 1) + CLRF_BYTES.length;
		int end = indexes.get(subsequenceNum);
		return new CharSequenceImpl(context, start, end);
	}

	private String getStringSubsequence(BufferContext context, List<Integer> indexes, int subsequenceNum) {
		return ByteUtils.extractSubsequence(
				context,
				indexes,
				CLRF_BYTES.length,
				subsequenceNum,
				new StringSubsequenceExtractor()
		);
	}

	private static class StringSubsequenceExtractor implements ByteUtils.SubsequenceExtractor<String> {
		private StringBuilder stringBuilder;

		@Override
		public void init(int size) {
			stringBuilder = new StringBuilder(size);
		}

		@Override
		public void append(byte b) {
			stringBuilder.append((char) b);
		}

		@Override
		public String getResult() {
			return stringBuilder.toString();
		}
	}

}
