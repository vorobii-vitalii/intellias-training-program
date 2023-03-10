package tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

public class TCPClientImpl implements TCPClient {
	private final TCPClientConfig clientConfig;
	private final SelectorProvider selectorProvider;

	public TCPClientImpl(TCPClientConfig clientConfig, SelectorProvider selectorProvider) {
		this.clientConfig = clientConfig;
		this.selectorProvider = selectorProvider;
	}

	/**
	 * Connects to socket
	 */
	public TCPConnection createConnection() throws IOException {
		var socketChannel = selectorProvider.openSocketChannel(clientConfig.getProtocolFamily());
		establishConnectionBlocking(socketChannel);
		var byteBuffer = ByteBuffer.allocateDirect(clientConfig.getBufferSize());
		return new TCPConnectionImpl(socketChannel, byteBuffer);
	}

	private void establishConnectionBlocking(SocketChannel socketChannel) throws IOException {
		socketChannel.configureBlocking(true);
		socketChannel.connect(new InetSocketAddress(clientConfig.getHost(), clientConfig.getPort()));
		socketChannel.configureBlocking(false);
	}
}
