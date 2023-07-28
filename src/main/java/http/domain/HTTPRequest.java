package http.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import reactor.util.annotation.NonNull;
import util.Constants;

public class HTTPRequest {
	private final HTTPRequestLine httpRequestLine;
	private final HTTPHeaders headers;
	private final byte[] body;

	public HTTPRequest(HTTPRequestLine httpRequestLine, HTTPHeaders httpHeaders, byte[] body) {
		this.httpRequestLine = httpRequestLine;
		this.headers = httpHeaders;
		this.body = body;
	}

	public HTTPRequest(HTTPRequestLine httpRequestLine) {
		this(httpRequestLine, new HTTPHeaders(), null);
	}

	@NonNull
	public List<String> getSupportedProtocols() {
		return getHeaders()
				.getHeaderValue(Constants.HTTPHeaders.WEBSOCKET_PROTOCOL)
				.map(s -> s.trim().split(","))
				.stream()
				.flatMap(Arrays::stream)
				.toList();
	}

	public HTTPHeaders getHeaders() {
		return headers;
	}

	public HTTPRequestLine getHttpRequestLine() {
		return httpRequestLine;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		HTTPRequest that = (HTTPRequest) o;
		return Objects.equals(httpRequestLine, that.httpRequestLine) && Objects.equals(headers, that.headers) && Arrays.equals(body, that.body);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(httpRequestLine, headers);
		result = 31 * result + Arrays.hashCode(body);
		return result;
	}

	@Override
	public String toString() {
		return "HTTPRequest{" +
						"httpRequestLine=" + httpRequestLine +
						", headers=" + headers +
						", payload=" + Arrays.toString(body) +
						'}';
	}
}
