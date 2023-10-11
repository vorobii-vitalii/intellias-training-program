package http.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import http.domain.HTTPHeaders;
import http.domain.HTTPMethod;
import http.domain.HTTPRequest;
import http.domain.HTTPRequestLine;
import http.domain.HTTPResponse;
import http.domain.HTTPResponseLine;
import http.domain.HTTPVersion;
import http.post_processor.HTTPResponsePostProcessor;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import request_handler.NetworkRequest;
import tcp.server.impl.MessageSerializerImpl;
import tcp.server.OperationType;
import tcp.server.SocketConnection;
import util.Constants;

@ExtendWith(MockitoExtension.class)
class TestHTTPNetworkSipMessageHandler {

	public static final HTTPRequest HTTP_REQUEST = new HTTPRequest(
			new HTTPRequestLine(HTTPMethod.GET, "/path", new HTTPVersion(1, 1))
	);
	public static final ByteBuffer BYTE_BUFFER = ByteBuffer.wrap(new byte[] {2, 1, 24, 12});
	public static final HTTPResponse HTTP_RESPONSE = new HTTPResponse(
			new HTTPResponseLine(new HTTPVersion(1, 1), 200, "OK"),
			new HTTPHeaders(),
			new byte[] {}
	);
	@Mock
	HTTPResponsePostProcessor httpResponsePostProcessor;

	@Mock
	HTTPRequestHandlerStrategy httpRequestHandlerStrategy;

	@Mock
	Context context;

	@Mock
	SocketConnection socketConnection;

	@Mock
	MessageSerializerImpl messageSerializer;

	HTTPNetworkRequestHandler httpNetworkRequestHandler;

	@BeforeEach
	void init() {
		httpNetworkRequestHandler = new HTTPNetworkRequestHandler(
				Runnable::run,
				List.of(httpRequestHandlerStrategy),
				List.of(httpResponsePostProcessor),
				messageSerializer,
				TracerProvider.noop().get("name"),
				() -> context
		);
	}

	@Test
	void handleGivenNoStrategyCanHandleTheRequest() {
		when(messageSerializer.serialize(any(), any())).thenReturn(BYTE_BUFFER);
		httpNetworkRequestHandler.handle(new NetworkRequest<>(HTTP_REQUEST, socketConnection));
		verify(socketConnection).appendResponse(BYTE_BUFFER);
		verify(socketConnection).changeOperation(OperationType.WRITE);
		verify(messageSerializer).serialize(argThat(v -> {
			var httpResponse = (HTTPResponse) v;
			return httpResponse.responseLine().statusCode() == Constants.HTTPStatusCode.NOT_FOUND;
		}), any());
	}

	@Test
	void handleGivenStrategyThatCanHandleTheRequestFound() {
		when(httpRequestHandlerStrategy.supports(HTTP_REQUEST)).thenReturn(true);
		when(httpRequestHandlerStrategy.handleRequest(HTTP_REQUEST)).thenReturn(HTTP_RESPONSE);
		when(messageSerializer.serialize(any(), any())).thenReturn(BYTE_BUFFER);
		httpNetworkRequestHandler.handle(new NetworkRequest<>(HTTP_REQUEST, socketConnection));
		verify(socketConnection).appendResponse(BYTE_BUFFER);
		verify(socketConnection).changeOperation(OperationType.WRITE);
		verify(messageSerializer).serialize(argThat(v -> {
			var httpResponse = (HTTPResponse) v;
			return Objects.equals(httpResponse, HTTP_RESPONSE);
		}), any());
	}

}