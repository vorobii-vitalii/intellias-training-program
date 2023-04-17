package websocket.handler;

import http.domain.*;
import http.handler.HTTPRequestHandlerStrategy;
import org.apache.commons.codec.digest.DigestUtils;
import util.Constants;
import websocket.endpoint.WebSocketEndpointProvider;

import java.util.Base64;
import java.util.Collection;
import java.util.function.Predicate;

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

	private final Collection<Predicate<HTTPRequest>> requestValidators;
	private final WebSocketEndpointProvider webSocketEndpointProvider;

	public WebSocketChangeProtocolHTTPHandlerStrategy(
					Collection<Predicate<HTTPRequest>> requestValidators,
					WebSocketEndpointProvider webSocketEndpointProvider
	) {
		this.requestValidators = requestValidators;
		this.webSocketEndpointProvider = webSocketEndpointProvider;
	}

	@Override
	public boolean supports(HTTPRequest httpRequest) {
		return requestValidators.stream().allMatch(predicate -> predicate.test(httpRequest));
	}

	@Override
	public HTTPResponse handleRequest(HTTPRequest request) {
		var endpoint = request.getHttpRequestLine().path();
		if (webSocketEndpointProvider.getEndpoint(endpoint) == null) {
			return new HTTPResponse(
							new HTTPResponseLine(
											new HTTPVersion(1, 1),
											Constants.HTTPStatusCode.NOT_FOUND,
											"Server doesn't have mapping for " + endpoint + " endpoint"
							),
							new HTTPHeaders(),
							new byte[]{}
			);
		}
		var webSocketKey = request.getHeaders().getHeaderValue(Constants.HTTPHeaders.WEBSOCKET_KEY).orElseThrow();
		return new HTTPResponse(
						new HTTPResponseLine(
										new HTTPVersion(1, 1),
										Constants.HTTPStatusCode.SWITCHING_PROTOCOL,
										"Switching protocol"
						),
						new HTTPHeaders()
										.addSingleHeader(Constants.HTTPHeaders.UPGRADE, "websocket")
										.addSingleHeader(Constants.HTTPHeaders.CONNECTION, "upgrade")
										.addSingleHeader(Constants.HTTPHeaders.WEBSOCKET_ACCEPT, calculateSecWebSocketAccept(webSocketKey)),
						new byte[]{}
		);
	}

	@Override
	public int getPriority() {
		return PRIORITY;
	}

	private String calculateSecWebSocketAccept(String webSocketKey) {
		return Base64.getEncoder()
						.encodeToString(DigestUtils.sha1(webSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"));
	}
}
