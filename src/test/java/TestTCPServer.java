import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tcp.ServerOperationType;
import tcp.TCPServer;
import tcp.TCPServerConfig;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestTCPServer {
	private static final StandardProtocolFamily PROTOCOL_FAMILY = StandardProtocolFamily.INET;
	private static final String HOST = "127.0.0.1";
	private static final int PORT = 8925;
	public static final int TIMEOUT = 5000;

	@Mock
	Consumer<Throwable> errorHandler;
	@Mock
	Consumer<SelectionKey> acceptConsumer;
	@Mock
	Consumer<SelectionKey> readConsumer;
	@Mock
	Consumer<SelectionKey> writeConsumer;
	@Mock
	SelectorProvider selectorProvider;
	@Mock
	AbstractSelector selector;
	@Mock
	ServerSocketChannel serverSocketChannel;
	@Mock
	SelectionKey selectionKey;
	TCPServer tCPServer;

	@BeforeEach
	void init() {
		tCPServer = new TCPServer(
				TCPServerConfig.builder()
						.setProtocolFamily(PROTOCOL_FAMILY)
						.setHost(HOST)
						.setPort(PORT)
						.build(),
				selectorProvider,
				errorHandler,
				Map.of(
						ServerOperationType.ACCEPT, acceptConsumer,
						ServerOperationType.READ, readConsumer,
						ServerOperationType.WRITE, writeConsumer
				));
	}

	@Test
	void testGivenAcceptOperation() throws IOException {
		when(selectorProvider.openSelector()).thenReturn(selector);
		when(selectorProvider.openServerSocketChannel(PROTOCOL_FAMILY)).thenReturn(serverSocketChannel);
		when(selector.select(any())).thenAnswer(invocationOnMock -> {
			Consumer<SelectionKey> selectionKeyConsumer = invocationOnMock.getArgument(0);
			when(selectionKey.isValid()).thenReturn(true);
			when(selectionKey.isAcceptable()).thenReturn(true);
			selectionKeyConsumer.accept(selectionKey);
			return null;
		});
		tCPServer.start();
		verify(selector, timeout(1000).atLeastOnce()).select(any());
		verify(acceptConsumer, atLeastOnce()).accept(selectionKey);
		verify(readConsumer, never()).accept(selectionKey);
		verify(writeConsumer, never()).accept(selectionKey);
	}

	@Test
	void testGivenReadOperation() throws IOException {
		when(selectorProvider.openSelector()).thenReturn(selector);
		when(selectorProvider.openServerSocketChannel(PROTOCOL_FAMILY)).thenReturn(serverSocketChannel);
		when(selector.select(any())).thenAnswer(invocationOnMock -> {
			Consumer<SelectionKey> selectionKeyConsumer = invocationOnMock.getArgument(0);
			when(selectionKey.isValid()).thenReturn(true);
			when(selectionKey.isReadable()).thenReturn(true);
			selectionKeyConsumer.accept(selectionKey);
			return null;
		});
		tCPServer.start();
		verify(selector, timeout(1000).atLeastOnce()).select(any());
		verify(readConsumer, atLeastOnce()).accept(selectionKey);
		verify(acceptConsumer, never()).accept(selectionKey);
		verify(writeConsumer, never()).accept(selectionKey);
	}

	@Test
	void testGivenWriteOperation() throws IOException {
		when(selectorProvider.openSelector()).thenReturn(selector);
		when(selectorProvider.openServerSocketChannel(PROTOCOL_FAMILY)).thenReturn(serverSocketChannel);
		when(selector.select(any())).thenAnswer(invocationOnMock -> {
			Consumer<SelectionKey> selectionKeyConsumer = invocationOnMock.getArgument(0);
			when(selectionKey.isValid()).thenReturn(true);
			when(selectionKey.isWritable()).thenReturn(true);
			selectionKeyConsumer.accept(selectionKey);
			return null;
		});
		tCPServer.start();
		verify(selector, timeout(1000).atLeastOnce()).select(any());
		verify(writeConsumer, atLeastOnce()).accept(selectionKey);
		verify(readConsumer, never()).accept(selectionKey);
		verify(acceptConsumer, never()).accept(selectionKey);
	}

	@Test
	void testStop() throws IOException, InterruptedException {
		when(selectorProvider.openSelector()).thenReturn(selector);
		when(selectorProvider.openServerSocketChannel(PROTOCOL_FAMILY)).thenReturn(serverSocketChannel);
		tCPServer.start();
		verify(selector, timeout(1000).atLeastOnce()).select(any());
		tCPServer.stop(TIMEOUT);
		Mockito.clearInvocations(selector);
		Thread.sleep(100);
		verifyNoInteractions(selector);
	}

}
