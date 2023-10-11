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
		String UPGRADE = "Upgrade".toLowerCase();
		String HOST = "Host".toLowerCase();
		String CONNECTION = "Connection".toLowerCase();
		String WEBSOCKET_KEY = "Sec-WebSocket-Key".toLowerCase();
		String WEBSOCKET_VERSION = "Sec-WebSocket-Version".toLowerCase();
		String WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept".toLowerCase();
		String WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol".toLowerCase();
		String CONTENT_TYPE = "Content-Type".toLowerCase();
		String CONTENT_LENGTH = "Content-Length".toLowerCase();
	}

	interface WebSocketMetadata {
		String ENDPOINT = "endpoint";
		String SUB_PROTOCOL = "subProtocol";
	}

	interface Protocol {
		String HTTP = "HTTP";
		String SIP = "SIP";
		String WEB_SOCKET = "WebSocket";
	}

}
