package ws;

import http.HTTPRequest;
import http.HTTPResponse;

public class WebSocketRequestHandler implements RequestHandler<HTTPRequest, HTTPResponse> {

	@Override
	public void handle(ProcessingRequest<HTTPRequest, HTTPResponse> processingRequest) {
		var request = processingRequest.getRequest();
		
	}

}
