package tcp.server.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import tcp.server.Channel;

public class NIOChannel implements Channel {
	private final SocketChannel socketChannel;

	public NIOChannel(SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
	}

	@Override
	public int read(ByteBuffer byteBuffer) throws IOException {
		return socketChannel.read(byteBuffer);
	}

	@Override
	public int write(ByteBuffer buffer) throws IOException {
		return socketChannel.write(buffer);
	}

	@Override
	public boolean isOpen() {
		return socketChannel.isOpen();
	}

	@Override
	public void close() throws IOException {
		socketChannel.close();
	}

	@Override
	public SocketAddress getLocalAddress() {
		try {
			return socketChannel.getLocalAddress();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
