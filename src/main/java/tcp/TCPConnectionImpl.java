package tcp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.function.Function;

public class TCPConnectionImpl implements TCPConnection {
	private final SocketChannel socketChannel;
	private final ByteBuffer buffer;

	public TCPConnectionImpl(SocketChannel socketChannel, ByteBuffer buffer) {
		assertNotNull(socketChannel, "socketChannel");
		assertNotNull(buffer, "buffer");
		this.socketChannel = socketChannel;
		this.buffer = buffer;
	}

	public void write(ByteBuffer buffer) throws IOException {
		while (buffer.hasRemaining()) {
			socketChannel.write(buffer);
		}
	}

	public void read(Function<ByteBuffer, Boolean> function) throws IOException {
		while (!Thread.currentThread().isInterrupted()) {
			socketChannel.read(buffer);
			buffer.flip();
			if (!function.apply(buffer)) {
				break;
			}
			buffer.clear();
		}
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
