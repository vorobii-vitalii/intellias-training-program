package sip.reactor_netty;

import io.netty.buffer.ByteBuf;
import tcp.server.BytesSource;

public class NettyBufferBytesSource implements BytesSource {
	private final ByteBuf byteBuf;

	public NettyBufferBytesSource(ByteBuf byteBuf) {
		this.byteBuf = byteBuf;
	}

	@Override
	public byte[] extract(int from, int end) {
		var bytes = new byte[end - from];
		byteBuf.getBytes(from, bytes);
		return bytes;
	}

	@Override
	public byte get(int pos) {
		return byteBuf.getByte(pos);
	}

	@Override
	public int size() {
		return byteBuf.readableBytes();
	}
}
