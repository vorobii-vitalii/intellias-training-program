package http.post_processor;

import http.domain.HTTPRequest;
import http.domain.HTTPResponse;
import request_handler.NetworkRequest;

public interface HTTPResponsePostProcessor {
	void handle(NetworkRequest<HTTPRequest> httpNetworkRequest, HTTPResponse response);
}
