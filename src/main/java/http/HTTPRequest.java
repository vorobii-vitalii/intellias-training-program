package http;

public class HTTPRequest {
	private HTTPRequestLine httpRequestLine;
	private final HTTPHeaders headers = new HTTPHeaders();

	public HTTPHeaders getHeaders() {
		return headers;
	}

	public HTTPRequestLine getHttpRequestLine() {
		return httpRequestLine;
	}

	public void setHttpRequestLine(HTTPRequestLine httpRequestLine) {
		this.httpRequestLine = httpRequestLine;
	}

}
