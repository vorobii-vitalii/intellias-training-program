package http.handler;

import tcp.server.reader.exception.ParseException;
import http.domain.HTTPMethod;
import http.domain.HTTPRequest;
import http.domain.HTTPRequestLine;
import http.domain.HTTPVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import request_handler.NetworkRequest;
import tcp.server.BufferContext;
import tcp.server.ServerAttachment;
import tcp.server.SocketMessageReader;
import tcp.server.handler.GenericReadOperationHandler;
import util.Constants;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenericReadOperationHandlerTest {
	private static final BufferContext BUFFER_CONTEXT = new BufferContext();
	private static final ServerAttachment SERVER_ATTACHMENT = new ServerAttachment(
					Constants.Protocol.HTTP,
					BUFFER_CONTEXT,
					new LinkedBlockingDeque<>(),
					new HashMap<>()
	);
	public static final HTTPRequest HTTP_REQUEST = new HTTPRequest(new HTTPRequestLine(HTTPMethod.GET, "/hi", new HTTPVersion(1, 1)));

	@Mock
	SocketMessageReader<HTTPRequest> socketMessageReader;

	@Mock
	BlockingQueue<NetworkRequest<HTTPRequest>> blockingQueue;

	@InjectMocks
	GenericReadOperationHandler<HTTPRequest> httpReadOperationHandler;

	@Mock
	SelectionKey selectionKey;

	@Mock
	SocketChannel socketChannel;

	@Test
	void acceptGivenMessageRead() throws IOException, ParseException {
		when(selectionKey.attachment()).thenReturn(SERVER_ATTACHMENT);
		when(selectionKey.channel()).thenReturn(socketChannel);
		when(socketMessageReader.readMessage(BUFFER_CONTEXT, socketChannel))
						.thenReturn(HTTP_REQUEST);
		httpReadOperationHandler.accept(selectionKey);
		verify(blockingQueue).add(argThat(request -> request.request().equals(HTTP_REQUEST)));
	}

	@Test
	void acceptGivenMessageNotRead() throws IOException, ParseException {
		when(selectionKey.attachment()).thenReturn(SERVER_ATTACHMENT);
		when(selectionKey.channel()).thenReturn(socketChannel);
		when(socketMessageReader.readMessage(BUFFER_CONTEXT, socketChannel))
						.thenReturn(null);
		httpReadOperationHandler.accept(selectionKey);
		verify(blockingQueue, never()).add(any());
	}

}