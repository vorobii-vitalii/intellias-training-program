package websocket.handler;

import http.*;
import http.handler.HTTPRequestHandlerStrategy;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.Base64;

/*

 The |Connection| and |Upgrade| header fields complete the HTTP
   Upgrade.  The |Sec-WebSocket-Accept| header field indicates whether
   the server is willing to accept the connection.  If present, this
   header field must include a hash of the client's nonce sent in
   |Sec-WebSocket-Key| along with a predefined GUID.  Any other value
   must not be interpreted as an acceptance of the connection by the
   server.

        HTTP/1.1 101 Switching Protocols
        Upgrade: websocket
        Connection: Upgrade
        Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=

 */
public class WebSocketChangeProtocolHTTPHandlerStrategy implements HTTPRequestHandlerStrategy {
	private static final int PRIORITY = 100;
	private static final Integer WS_VERSION = 13;
	private static final int CODE_SWITCHING_PROTOCOL = 101;

	@Override
	public boolean supports(HTTPRequest httpRequest) {
		var requestLine = httpRequest.getHttpRequestLine();
		if (requestLine.httpMethod() != HTTPMethod.GET) {
			return false;
		}
		var headers = httpRequest.getHeaders();
		// TODO: Add validation of value
		if (headers.getHeaderValues("Host").isEmpty()) {
			return false;
		}
		// TODO: Check origin to implement CORS
		if (!headers.getHeaderValues("Upgrade").contains("websocket")) {
			return false;
		}
		if (!headers.getHeaderValues("Connection").contains("Upgrade")) {
			return false;
		}
		if (headers.getHeaderValues("Sec-WebSocket-Key").isEmpty()) {
			return false;
		}
		if (!headers.getHeaderValues("Sec-WebSocket-Version").contains(String.valueOf(WS_VERSION))) {
			return false;
		}
		return true;
	}

	@Override
	public HTTPResponse handleRequest(HTTPRequest request) {
		var webSocketKey = request.getHeaders().getHeaderValues("Sec-WebSocket-Key").get(0);
		return new HTTPResponse(
						new HTTPResponseLine(
							new HTTPVersion(1, 1),
							CODE_SWITCHING_PROTOCOL,
							"Switching protocol"
						),
						new HTTPHeaders()
								.addHeader("Upgrade", "websocket")
								.addHeader("Connection", "upgrade")
								.addHeader("Sec-WebSocket-Accept", calcSecWebSocketAccept(webSocketKey)),
						new byte[] {}
		);
	}

	@Override
	public int getPriority() {
		return PRIORITY;
	}

	private String calcSecWebSocketAccept(String webSocketKey) {
		return Base64.getEncoder()
						.encodeToString(DigestUtils.sha1(webSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"));
	}
}

