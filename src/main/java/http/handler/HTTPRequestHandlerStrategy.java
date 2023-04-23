package http.handler;

import http.domain.HTTPRequest;
import http.domain.HTTPResponse;

public interface HTTPRequestHandlerStrategy {
	boolean supports(HTTPRequest httpRequest);
	HTTPResponse handleRequest(HTTPRequest request);

	int getPriority();
}
