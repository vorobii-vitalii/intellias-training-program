package websocket.reader;

import exception.ParseException;
import reader.MessageReader;

import java.nio.ByteBuffer;
import java.util.Arrays;

public abstract class AbstractConstantBytesMessageReader<T> implements MessageReader<T> {
	private final byte[] bytes;
	private int pos = 0;

	public AbstractConstantBytesMessageReader(int bytesToRead) {
		this.bytes = new byte[bytesToRead];
	}

	@Override
	public MessageReader<T> read(ByteBuffer buffer) throws ParseException {
		while (!isEnoughBytesRead() && buffer.hasRemaining()) {
			bytes[pos++] = buffer.get();
		}
		if (isEnoughBytesRead()) {
			return onBytesRead(bytes);
		}
		return this;
	}

	public abstract MessageReader<T> onBytesRead(byte[] bytes);

	private boolean isEnoughBytesRead() {
		return pos == bytes.length;
	}
}
