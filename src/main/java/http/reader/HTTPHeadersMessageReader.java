package http.reader;

import exception.ParseException;
import http.HTTPRequest;
import reader.MessageReader;
import reader.CompletedMessageReader;

import java.nio.ByteBuffer;

public class HTTPHeadersMessageReader implements MessageReader<HTTPRequest> {
	private static final int NOT_FOUND_INDEX = -1;
	private static final char CARRIAGE_RETURN = '\r';
	private static final char LINE_FEED = '\n';
	private static final char HEADER_DELIMITER = ':';

	private final HTTPRequest httpRequest;
	private final StringBuilder stringBuilder = new StringBuilder();

	public HTTPHeadersMessageReader(HTTPRequest httpRequest) {
		this.httpRequest = httpRequest;
	}

	@Override
	public MessageReader<HTTPRequest> read(ByteBuffer buffer) throws ParseException {
		while (buffer.hasRemaining()) {
			var b = buffer.get();
			stringBuilder.append((char) b);
			if (isEndOfHeaders()) {
				// TODO: Add support of body
				return new CompletedMessageReader<>(httpRequest);
			}
			else if (isCLRFDetected()) {
				var headerLine = getHeaderLine();
				var indexOfHeaderDelimiter = headerLine.indexOf(HEADER_DELIMITER);
				if (indexOfHeaderDelimiter == NOT_FOUND_INDEX) {
					throw new ParseException("HTTP header key-value pair should be delimiter-ed by : character");
				}
				httpRequest.getHeaders()
								.addHeader(
												headerLine.substring(0, indexOfHeaderDelimiter),
												headerLine.substring(indexOfHeaderDelimiter + 1)
								);
				stringBuilder.setLength(0);
			}
		}
		return this;
	}
	private String getHeaderLine() {
		return stringBuilder.substring(0, stringBuilder.length() - 2);
	}

	private boolean isEndOfHeaders() {
		return stringBuilder.length() == 2 && isCLRFDetected();
	}

	private boolean isCLRFDetected() {
		return compareFromEnd(1, CARRIAGE_RETURN) && compareFromEnd(0, LINE_FEED);
	}

	private boolean compareFromEnd(int index, char c) {
		return stringBuilder.length() - index >= 1 && stringBuilder.charAt(stringBuilder.length() - index - 1) == c;
	}

}
