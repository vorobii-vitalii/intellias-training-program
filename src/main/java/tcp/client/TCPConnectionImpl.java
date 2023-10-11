package tcp.client;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public record TCPConnectionImpl(SocketChannel socketChannel) implements TCPConnection {
	public TCPConnectionImpl {
		assertNotNull(socketChannel, "socketChannel");
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
