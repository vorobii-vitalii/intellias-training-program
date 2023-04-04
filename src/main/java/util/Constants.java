package util;

public interface Constants {

	interface HTTPStatusCode {
		int SWITCHING_PROTOCOL = 101;
		int BAD_REQUEST = 400;
		int NOT_FOUND = 404;
	}

	interface HTTPHeaders {
		String UPGRADE = "Upgrade";
		String HOST = "Host";
		String CONNECTION = "Connection";
		String WEBSOCKET_KEY = "Sec-WebSocket-Key";
		String WEBSOCKET_VERSION = "Sec-WebSocket-Version";
		String WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";
		String WEBSOCKET_PROTOCOLS = "Sec-WebSocket-Protocol";
	}

	interface WebSocketMetadata {
		String ENDPOINT = "endpoint";
		String PROTOCOL = "protocol";
	}

	interface Protocol {
		String HTTP = "HTTP";
		String WEB_SOCKET = "WebSocket";
	}

}
