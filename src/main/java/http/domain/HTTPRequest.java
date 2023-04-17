package http.domain;

import java.util.Objects;

public class HTTPRequest {
	private final HTTPRequestLine httpRequestLine;
	private final HTTPHeaders headers = new HTTPHeaders();

	public HTTPRequest(HTTPRequestLine httpRequestLine) {
		this.httpRequestLine = httpRequestLine;
	}

	public HTTPRequest addHeader(String headerName, String headerValue) {
		headers.addSingleHeader(headerName, headerValue);
		return this;
	}

	public HTTPHeaders getHeaders() {
		return headers;
	}

	public HTTPRequestLine getHttpRequestLine() {
		return httpRequestLine;
	}

	@Override
	public String toString() {
		return "HTTPRequest{" +
						"httpRequestLine=" + httpRequestLine +
						", headers=" + headers +
						'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		HTTPRequest that = (HTTPRequest) o;
		return Objects.equals(httpRequestLine, that.httpRequestLine) && Objects.equals(headers, that.headers);
	}

	@Override
	public int hashCode() {
		return Objects.hash(httpRequestLine, headers);
	}
}
