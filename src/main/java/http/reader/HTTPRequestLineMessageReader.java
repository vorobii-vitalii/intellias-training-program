package http.reader;

import exception.ParseException;
import http.HTTPRequest;
import http.HTTPRequestLine;
import reader.MessageReader;

import java.nio.ByteBuffer;

public class HTTPRequestLineMessageReader implements MessageReader<HTTPRequest> {
	private static final char CARRIAGE_RETURN = '\r';
	private static final char LINE_FEED = '\n';

	private final StringBuilder stringBuilder = new StringBuilder();
	private final HTTPRequest httpRequest;

	public HTTPRequestLineMessageReader(HTTPRequest requestMessage) {
		this.httpRequest = requestMessage;
	}

	@Override
	public MessageReader<HTTPRequest> read(ByteBuffer buffer) throws ParseException {
		while (buffer.hasRemaining()) {
			var b = buffer.get();
			stringBuilder.append((char) b);
			if (isCLRFDetected()) {
				var httpRequestLine = HTTPRequestLine.parse(getRequestLine());
				httpRequest.setHttpRequestLine(httpRequestLine);
				return new HTTPHeadersMessageReader(httpRequest);
			}
		}
		return this;
	}

	private String getRequestLine() {
		return stringBuilder.substring(0, stringBuilder.length() - 2);
	}

	private boolean isCLRFDetected() {
		return compareFromEnd(1, CARRIAGE_RETURN) && compareFromEnd(0, LINE_FEED);
	}

	private boolean compareFromEnd(int index, char c) {
		return stringBuilder.length() - index >= 1 && stringBuilder.charAt(stringBuilder.length() - index - 1) == c;
	}

}
