package writer;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class MessageWriter {
	private final ByteBuffer bufferToWrite;
	private final Runnable onMessageWritten;

	public MessageWriter(ByteBuffer bufferToWrite, Runnable onMessageWritten) {
		this.bufferToWrite = bufferToWrite;
		this.onMessageWritten = onMessageWritten;
	}

	public void write(Consumer<ByteBuffer> byteBufferConsumer) {
		if (!bufferToWrite.hasRemaining()) {
			throw new IllegalStateException("Buffer already written...");
		}
		byteBufferConsumer.accept(bufferToWrite);
		if (!bufferToWrite.hasRemaining()) {
			onMessageWritten.run();
		}
	}

	public boolean isWritten() {
		return !bufferToWrite.hasRemaining();
	}

}
