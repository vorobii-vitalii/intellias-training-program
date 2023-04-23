package http.reader;

import http.domain.HTTPHeaders;
import http.domain.HTTPRequest;
import http.domain.HTTPRequestLine;
import tcp.server.BufferContext;
import tcp.server.reader.MessageReader;
import tcp.server.reader.exception.ParseException;
import util.ByteUtils;
import util.Constants;
import util.Pair;

import java.util.List;
import java.util.function.BiFunction;

public class HTTPRequestMessageReader implements MessageReader<HTTPRequest> {
	private static final byte CARRIAGE_RETURN = '\r';
	private static final byte LINE_FEED = '\n';
	private static final byte[] CLRF_BYTES = new byte[]{CARRIAGE_RETURN, LINE_FEED};
	private static final char HEADER_DELIMITER = ':';
	private static final int NOT_FOUND = -1;

	private final BiFunction<String, String, List<String>> headerValuesExtractor;

	public HTTPRequestMessageReader(BiFunction<String, String, List<String>> headerValuesExtractor) {
		this.headerValuesExtractor = headerValuesExtractor;
	}

	@Override
	public Pair<HTTPRequest, Integer> read(BufferContext bufferContext) throws ParseException {
		var indexes = ByteUtils.findAllMatches(bufferContext, CLRF_BYTES);
		var headerEndIndex = ByteUtils.getIndexOfRepetitiveSubsequence(indexes, CLRF_BYTES.length);
		if (headerEndIndex == NOT_FOUND) {
			return null;
		}
		var requestLine = HTTPRequestLine.parse(getStringSubsequence(bufferContext, indexes, 0));
		var httpHeaders = new HTTPHeaders();
		for (var i = 1; i < indexes.size() - 1; i++) {
			var line = getStringSubsequence(bufferContext, indexes, i);
			var headerDelimiterIndex = line.indexOf(HEADER_DELIMITER);
			if (headerDelimiterIndex == NOT_FOUND) {
				throw new ParseException("HTTP header key-value pair should be delimiter-ed by : character " + line);
			}
			var headerName = line.substring(0, headerDelimiterIndex);
			var headerValues = headerValuesExtractor.apply(headerName, line.substring(headerDelimiterIndex + 1));
			httpHeaders.addHeaders(headerName, headerValues);
		}
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
		return new Pair<>(new HTTPRequest(requestLine, httpHeaders, body), bodyStartIndex + payloadSize);
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
