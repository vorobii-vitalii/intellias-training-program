package http.handler;

import http.HTTPRequest;
import http.HTTPResponse;

public interface HTTPRequestHandlerStrategy {
	boolean supports(HTTPRequest httpRequest);
	HTTPResponse handleRequest(HTTPRequest request);

	int getPriority();
}
