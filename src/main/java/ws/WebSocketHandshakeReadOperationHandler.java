package ws;

import http.HTTPRequest;
import http.HTTPResponse;

import java.nio.channels.SelectionKey;
import java.util.concurrent.BlockingQueue;

public class WebSocketHandshakeReadOperationHandler
				extends GenericRequestResponseReadOperationHandler<HTTPRequest, HTTPResponse> {

	public WebSocketHandshakeReadOperationHandler(
			BlockingQueue<ProcessingRequest<HTTPRequest, HTTPResponse>> requestQueue
	) {
		super(requestQueue);
	}

	@Override
	public void onMessageResponse(HTTPResponse httpResponse, SelectionKey selectionKey) {

	}
}
