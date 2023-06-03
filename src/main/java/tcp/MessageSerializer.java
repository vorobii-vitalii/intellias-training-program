package tcp;

import java.nio.ByteBuffer;

import tcp.server.ByteBufferPool;
import tcp.server.EventEmitter;
import util.Serializable;

public class MessageSerializer {
	public static final EventEmitter NOOP = e -> {
	};
	private final ByteBufferPool byteBufferPool;

	public MessageSerializer(ByteBufferPool byteBufferPool) {
		this.byteBufferPool = byteBufferPool;
	}

	public ByteBuffer serialize(Serializable serializable, EventEmitter eventEmitter) {
		var newBuffer = byteBufferPool.allocate(serializable.getSize());
		eventEmitter.emit("Buffer allocated");
		serializable.serialize(newBuffer);
		eventEmitter.emit("Message serialized");
		newBuffer.flip();
		return newBuffer;
	}

	public ByteBuffer serialize(Serializable serializable) {
		return serialize(serializable, NOOP);
	}

}