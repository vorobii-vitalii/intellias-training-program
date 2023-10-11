package websocket.handler;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.commons.codec.digest.DigestUtils;

import http.domain.HTTPHeaders;
import http.domain.HTTPRequest;
import http.domain.HTTPResponse;
import http.domain.HTTPResponseLine;
import http.domain.HTTPVersion;
import http.handler.HTTPRequestHandlerStrategy;
import util.Constants;
import websocket.endpoint.WebSocketEndpointProvider;

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
	private final Set<String> supportedProtocols;

	public WebSocketChangeProtocolHTTPHandlerStrategy(
			Collection<Predicate<HTTPRequest>> requestValidators,
			WebSocketEndpointProvider webSocketEndpointProvider
	) {
		this(requestValidators, webSocketEndpointProvider, Set.of());
	}

	public WebSocketChangeProtocolHTTPHandlerStrategy(
			Collection<Predicate<HTTPRequest>> requestValidators,
			WebSocketEndpointProvider webSocketEndpointProvider,
			Set<String> supportedProtocols
	) {
		this.requestValidators = requestValidators;
		this.webSocketEndpointProvider = webSocketEndpointProvider;
		this.supportedProtocols = supportedProtocols;
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
					new byte[] {}
			);
		}
		var webSocketKey = request.getHeaders().getHeaderValue(Constants.HTTPHeaders.WEBSOCKET_KEY).orElseThrow();
		var httpHeaders = new HTTPHeaders()
				.addSingleHeader(Constants.HTTPHeaders.UPGRADE, "websocket")
				.addSingleHeader(Constants.HTTPHeaders.CONNECTION, "upgrade")
				.addSingleHeader(Constants.HTTPHeaders.WEBSOCKET_ACCEPT, calculateSecWebSocketAccept(webSocketKey));
		var requestedSubProtocols = request.getSupportedProtocols();
		if (requestedSubProtocols.isEmpty()) {
			return new HTTPResponse(
					new HTTPResponseLine(
							new HTTPVersion(1, 1),
							Constants.HTTPStatusCode.SWITCHING_PROTOCOL,
							"Switching protocol"
					),
					httpHeaders,
					new byte[] {}
			);
		}
		var firstSupportedSubProtocol = requestedSubProtocols.stream()
				.filter(supportedProtocols::contains)
				.findFirst()
				.orElse(null);
		if (firstSupportedSubProtocol == null) {
			return new HTTPResponse(
					new HTTPResponseLine(
							new HTTPVersion(1, 1),
							Constants.HTTPStatusCode.BAD_REQUEST,
							"No protocol is supported... " + requestedSubProtocols
					),
					httpHeaders,
					new byte[] {}
			);
		}
		return new HTTPResponse(
				new HTTPResponseLine(
						new HTTPVersion(1, 1),
						Constants.HTTPStatusCode.SWITCHING_PROTOCOL,
						"Switching protocol"
				),
				httpHeaders.addSingleHeader(Constants.HTTPHeaders.WEBSOCKET_PROTOCOL, firstSupportedSubProtocol),
				new byte[] {}
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
