package reader;

import exception.ParseException;
import reader.MessageReader;

import java.nio.ByteBuffer;

public class CompletedMessageReader<T> implements MessageReader<T> {
	private final T completedMessage;

	public CompletedMessageReader(T completedMessage) {
		this.completedMessage = completedMessage;
	}

	@Override
	public MessageReader<T> read(ByteBuffer buffer) throws ParseException {
		throw new IllegalStateException("The method should not be called");
	}

	@Override
	public boolean isReady() {
		return true;
	}

	@Override
	public T getMessage() {
		return completedMessage;
	}
}
