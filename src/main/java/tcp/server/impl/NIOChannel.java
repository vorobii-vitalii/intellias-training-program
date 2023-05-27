package tcp.server.impl;

import java.io.IOException;
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
}
