package udp;

import java.nio.ByteBuffer;

import tcp.server.BytesSource;

public class ByteBufferSource implements BytesSource {
	private final ByteBuffer byteBuffer;

	public ByteBufferSource(ByteBuffer byteBuffer) {
		this.byteBuffer = byteBuffer;
	}

	@Override
	public byte[] extract(int from, int end) {
		var size = end - from;
		var arr = new byte[size];
		byteBuffer.get(from, arr, 0, size);
		return arr;
	}

	@Override
	public byte get(int pos) {
		return byteBuffer.get(pos);
	}

	@Override
	public int size() {
		return byteBuffer.limit();
	}
}
