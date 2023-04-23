package tcp.server;

import tcp.server.reader.exception.ParseException;
import tcp.server.reader.MessageReader;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

public class SocketMessageReader<Message> {
	private final MessageReader<Message> messageReader;

	public SocketMessageReader(MessageReader<Message> messageReader) {
		this.messageReader = messageReader;
	}

	public Message readMessage(BufferContext bufferContext, ReadableByteChannel readableByteChannel)
					throws IOException, ParseException {
		var buffer = bufferContext.getAvailableBuffer();
		do {
			var res = messageReader.read(bufferContext);
			if (res != null) {
				bufferContext.free(res.second());
				return res.first();
			}
		}
		while (readableByteChannel.read(buffer) > 0);
		return null;
	}

}
