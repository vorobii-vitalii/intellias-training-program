package http.reader;

import exception.ParseException;
import http.HTTPRequest;
import http.HTTPRequestLine;
import reader.MessageReader;
import tcp.server.BufferContext;
import util.Utils;

import java.util.List;
import java.util.function.BiFunction;

public class HTTPRequestMessageReader implements MessageReader<HTTPRequest> {
	private static final byte CARRIAGE_RETURN = '\r';
	private static final byte LINE_FEED = '\n';
	private static final byte[] CLRF_BYTES = new byte[]{CARRIAGE_RETURN, LINE_FEED};
	private static final char HEADER_DELIMITER = ':';
	private static final int NOT_FOUND_INDEX = -1;

	private final BiFunction<String, String, List<String>> headerValuesExtractor;

	public HTTPRequestMessageReader(BiFunction<String, String, List<String>> headerValuesExtractor) {
		this.headerValuesExtractor = headerValuesExtractor;
	}

	@Override
	public HTTPRequest read(BufferContext bufferContext) throws ParseException {
		var indexes = Utils.findAllMatches(bufferContext, CLRF_BYTES);
		var headerEndIndex = -1;
		for (var i = 0; i < indexes.size() - 1; i++) {
			if (indexes.get(i + 1) == indexes.get(i) + CLRF_BYTES.length) {
				headerEndIndex = i + 1;
				break;
			}
		}
		if (headerEndIndex == -1) {
			return null;
		}
		// TODO: Iterate over headers and try to find Content-Length parameter, if not null -> validate
		var request = new HTTPRequest(HTTPRequestLine.parse(extract(bufferContext, 0, indexes.get(0))));
		for (int i = 1; i != headerEndIndex; i++) {
			var line = extract(bufferContext, indexes.get(i - 1) + CLRF_BYTES.length, indexes.get(i));
			var headerDelimiterIndex = line.indexOf(HEADER_DELIMITER);
			if (headerDelimiterIndex == NOT_FOUND_INDEX) {
				throw new ParseException("HTTP header key-value pair should be delimiter-ed by : character");
			}
			var headerName = line.substring(0, headerDelimiterIndex);
			var headerValues = headerValuesExtractor.apply(headerName, line.substring(headerDelimiterIndex + 1));
			request.getHeaders().addHeaders(headerName, headerValues);
		}
		return request;
	}

	private String extract(BufferContext context, int start, int end) {
		var builder = new StringBuilder();
		for (var i = start; i < end; i++) {
			builder.append((char) (context.get(i)));
		}
		return builder.toString();
	}
}
