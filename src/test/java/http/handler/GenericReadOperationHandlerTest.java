package http.handler;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import message_passing.MessageProducer;
import tcp.server.BufferContext;
import tcp.server.Channel;
import tcp.server.ServerAttachmentImpl;
import tcp.server.reader.exception.ParseException;
import http.domain.HTTPMethod;
import http.domain.HTTPRequest;
import http.domain.HTTPRequestLine;
import http.domain.HTTPVersion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import request_handler.NetworkRequest;
import tcp.server.ServerAttachment;
import tcp.server.SocketMessageReader;
import tcp.server.handler.GenericReadOperationHandler;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenericReadOperationHandlerTest {
	public static final HTTPRequest HTTP_REQUEST = new HTTPRequest(
			new HTTPRequestLine(HTTPMethod.GET, "/hi", new HTTPVersion(1, 1)));
	public static final Tracer TRACER = TracerProvider.noop().get("");
	@Mock
	SocketMessageReader<HTTPRequest> socketMessageReader;

	@Mock
	MessageProducer<NetworkRequest<HTTPRequest>> messageProducer;

	@Mock
	BiConsumer<SelectionKey, Throwable> onError;

	GenericReadOperationHandler<HTTPRequest> httpReadOperationHandler;

	@Mock
	SelectionKey selectionKey;

	@Mock
	Channel socketChannel;

	@Mock
	Context context;

	@Mock
	ServerAttachment serverAttachment;

	@Mock
	BufferContext bufferContext;

	@BeforeEach
	void init() {
		httpReadOperationHandler = new GenericReadOperationHandler<>(messageProducer, socketMessageReader, onError, TRACER, () -> context);
		when(selectionKey.attachment()).thenReturn(serverAttachment);
	}

	@Test
	void acceptGivenMessageCannotBeRead() throws IOException, ParseException {
		when(serverAttachment.isReadable()).thenReturn(false);
		httpReadOperationHandler.accept(selectionKey);
		verify(socketMessageReader, never()).readMessage(any(), any(), any());
	}

	@Test
	void acceptGivenMessageRead() throws IOException, ParseException {
		when(serverAttachment.isReadable()).thenReturn(true);
		when(serverAttachment.bufferContext()).thenReturn(bufferContext);
		when(serverAttachment.getChannel()).thenReturn(socketChannel);
		when(socketMessageReader.readMessage(eq(bufferContext), eq(socketChannel), any()))
				.thenReturn(HTTP_REQUEST).thenReturn(null);
		httpReadOperationHandler.accept(selectionKey);
		verify(messageProducer).produce(argThat(request -> request.request().equals(HTTP_REQUEST)));
	}

	@Test
	void acceptGivenMessageNotRead() throws IOException, ParseException {
		when(serverAttachment.isReadable()).thenReturn(true);
		when(serverAttachment.bufferContext()).thenReturn(bufferContext);
		when(serverAttachment.getChannel()).thenReturn(socketChannel);
		when(socketMessageReader.readMessage(eq(bufferContext), eq(socketChannel), any()))
				.thenReturn(null);
		httpReadOperationHandler.accept(selectionKey);
		verify(messageProducer, never()).produce(any());
	}

}