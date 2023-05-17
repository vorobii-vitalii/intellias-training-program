package util;

public interface Constants {


	interface Tracing {
		String CONTEXT = "CONTEXT";
	}

	interface HTTPStatusCode {
		int SWITCHING_PROTOCOL = 101;
		int BAD_REQUEST = 400;
		int NOT_FOUND = 404;
		int OK = 200;
	}

	interface HTTPHeaders {
		String UPGRADE = "Upgrade";
		String HOST = "Host";
		String CONNECTION = "Connection";
		String WEBSOCKET_KEY = "Sec-WebSocket-Key";
		String WEBSOCKET_VERSION = "Sec-WebSocket-Version";
		String WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";
		String CONTENT_TYPE = "Content-Type";
		String CONTENT_LENGTH = "Content-Length";
	}

	interface WebSocketMetadata {
		String ENDPOINT = "endpoint";
	}

	interface Protocol {
		String HTTP = "HTTP";
		String WEB_SOCKET = "WebSocket";
	}

}
