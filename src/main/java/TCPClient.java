import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.function.Function;

public class TCPClient implements AutoCloseable {
	private final TCPClientConfig clientConfig;
	private final SelectorProvider selectorProvider;
	private SocketChannel socketChannel;
	private ByteBuffer buffer;

	public TCPClient(TCPClientConfig clientConfig, SelectorProvider selectorProvider) {
		this.clientConfig = clientConfig;
		this.selectorProvider = selectorProvider;
	}

	/**
	 * Connects to socket
	 * Method blocks until TCP connection is established
	 */
	public void connect() throws IOException {
		if (socketChannel != null) {
			return;
		}
		socketChannel = selectorProvider.openSocketChannel(clientConfig.getProtocolFamily());
		socketChannel.configureBlocking(true);
		socketChannel.connect(new InetSocketAddress(clientConfig.getHost(), clientConfig.getPort()));
		buffer = ByteBuffer.allocateDirect(clientConfig.getBufferSize());
		socketChannel.configureBlocking(false);
	}

	@Override
	public void close() throws IOException {
		if (socketChannel == null) {
			return;
		}
		socketChannel.close();
		socketChannel = null;
		buffer = null;
	}

	public void write(ByteBuffer buffer) throws IOException {
		assertSocketConnectionEstablished();
		while (buffer.hasRemaining()) {
			socketChannel.write(buffer);
		}
	}

	/**
	 * Read from socket
	 * @param function - accepts chunk of data read from socket
	 *                    and returns true if more data is needed from socket, false otherwise
	 */
	public void read(Function<ByteBuffer, Boolean> function) throws IOException {
		assertSocketConnectionEstablished();
		while (!Thread.currentThread().isInterrupted()) {
			socketChannel.read(buffer);
			buffer.flip();
			if (!function.apply(buffer)) {
				break;
			}
			buffer.clear();
		}
	}

	private void assertSocketConnectionEstablished() {
		if (socketChannel == null) {
			throw new IllegalStateException("Connection to socket was not established...");
		}
	}

}
