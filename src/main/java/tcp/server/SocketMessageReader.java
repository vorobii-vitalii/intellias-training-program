package tcp.server;

import exception.ParseException;
import reader.MessageReader;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class SocketMessageReader<Message> {
	private final MessageReader<Message> messageReader;

	public SocketMessageReader(MessageReader<Message> messageReader) {
		this.messageReader = messageReader;
	}

	public Message readMessage(
					BufferContext bufferContext,
					SocketChannel socketChannel
	) throws IOException, ParseException {
		var buffer = bufferContext.getAvailableBuffer();
		while (socketChannel.read(buffer) > 0) {
			buffer = bufferContext.getAvailableBuffer();
		}
		var message = messageReader.read(bufferContext);
		if (message != null) {
			bufferContext.reset();
		}
		return message;
	}

}
