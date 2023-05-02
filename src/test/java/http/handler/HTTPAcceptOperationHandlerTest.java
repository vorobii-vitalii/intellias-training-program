package http.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tcp.server.ServerAttachment;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HTTPAcceptOperationHandlerTest {

	@Mock
	SelectionKey selectionKey;

	@Mock
	ServerSocketChannel serverSocketChannel;

	@Mock
	SocketChannel socketChannel;

	@Mock
	Selector selector;

	HTTPAcceptOperationHandler acceptOperationHandler = new HTTPAcceptOperationHandler();

	@Test
	void accept() throws IOException {
		when(selectionKey.channel()).thenReturn(serverSocketChannel);
		when(selectionKey.selector()).thenReturn(selector);
		when(serverSocketChannel.accept()).thenReturn(socketChannel);
		acceptOperationHandler.accept(selectionKey);
		verify(socketChannel).configureBlocking(false);
		verify(socketChannel).register(eq(selector), eq(SelectionKey.OP_READ), any(ServerAttachment.class));
	}

}