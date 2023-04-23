package http.domain;

import tcp.server.reader.exception.ParseException;

public record HTTPRequestLine(HTTPMethod httpMethod, String path, HTTPVersion httpVersion) {
	public static HTTPRequestLine parse(String requestLine) throws ParseException {
		var requestLineComponents = requestLine.split(" ");
		if (requestLineComponents.length != 3) {
			throw new ParseException("request-line   = method SP request-target SP HTTP-version CRLF");
		}
		var method = HTTPMethod.parse(requestLineComponents[0]);
		var path = requestLineComponents[1];
		var httpVersion = HTTPVersion.parse(requestLineComponents[2]);
		return new HTTPRequestLine(method, path, httpVersion);
	}

}
