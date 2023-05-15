package http.domain;

import tcp.BytesAccessor;
import tcp.server.reader.exception.ParseException;

public record HTTPRequestLine(HTTPMethod httpMethod, String path, HTTPVersion httpVersion) {

	public static final byte SPACE = ' ';

//	public static HTTPRequestLine parse(String requestLine) throws ParseException {
//		var requestLineComponents = requestLine.split(" ");
//		if (requestLineComponents.length != 3) {
//			throw new ParseException("request-line   = method SP request-target SP HTTP-version CRLF");
//		}
//		var method = HTTPMethod.parse(requestLineComponents[0]);
//		var path = requestLineComponents[1];
//		var httpVersion = HTTPVersion.parse(requestLineComponents[2]);
//		return new HTTPRequestLine(method, path, httpVersion);
//	}

	public static HTTPRequestLine parse(CharSequence charSequence) throws ParseException {
		var n = charSequence.length();
		var leftSpace = -1;
		for (var i = 0; i < n; i++) {
			if (charSequence.charAt(i) == SPACE) {
				leftSpace = i;
				break;
			}
		}
		int rightSpace = -1;
		for (int i = n - 1; i >= 0; i--) {
			if (charSequence.charAt(i) == SPACE) {
				rightSpace = i;
				break;
			}
		}
		if (leftSpace == -1 || rightSpace == -1 || leftSpace == rightSpace) {
			throw new ParseException("request-line   = method SP request-target SP HTTP-version CRLF");
		}
		var method = HTTPMethod.parse(charSequence.subSequence(0, leftSpace));
		var path = charSequence.subSequence(leftSpace + 1, rightSpace).toString();
		var httpVersion = HTTPVersion.parse(charSequence.subSequence(rightSpace + 1, n));
		return new HTTPRequestLine(method, path, httpVersion);
	}

	@Override
	public String toString() {
		return httpMethod + " " + path + " " + httpVersion;
	}
}
