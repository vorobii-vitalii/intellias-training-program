package tcp.client;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class TCPConnectionImpl implements TCPConnection {
	private final SocketChannel socketChannel;

	public TCPConnectionImpl(SocketChannel socketChannel) {
		assertNotNull(socketChannel, "socketChannel");
		this.socketChannel = socketChannel;
	}

	@Override
	public SocketChannel getSocketChannel() {
		return socketChannel;
	}

	@Override
	public void close() throws IOException {
		socketChannel.close();
	}

	private void assertNotNull(Object arg, String name) {
		if (arg == null) {
			throw new IllegalArgumentException(name + " not provided");
		}
	}

}
