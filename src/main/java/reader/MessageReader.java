package reader;

import exception.ParseException;

import java.nio.ByteBuffer;

public interface MessageReader<MessageObject> {
	MessageReader<MessageObject> read(ByteBuffer buffer) throws ParseException;
	default boolean isReady() {
		return false;
	}
	default MessageObject getMessage() {
		throw new IllegalStateException("Message is not ready yet");
	}
}
