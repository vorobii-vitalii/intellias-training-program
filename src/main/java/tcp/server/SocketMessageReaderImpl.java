package tcp.server;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tcp.server.reader.MessageReader;
import tcp.server.reader.exception.ParseException;

public class SocketMessageReaderImpl<Message> implements SocketMessageReader<Message> {
	private static final Logger LOGGER = LoggerFactory.getLogger(SocketMessageReaderImpl.class);
	private final MessageReader<Message> messageReader;

	public SocketMessageReaderImpl(MessageReader<Message> messageReader) {
		this.messageReader = messageReader;
	}

	@Override
	public Message readMessage(BufferContext bufferContext, Channel channel, EventEmitter eventEmitter) throws IOException, ParseException {
		while (true) {
			var buffer = bufferContext.getAvailableBuffer();
			eventEmitter.emit("Got buffer to write");
			if (channel.read(buffer) <= 0) {
				break;
			}
		}
		eventEmitter.emit("Read from context");
		var res = messageReader.read(bufferContext, eventEmitter);
		eventEmitter.emit("Read from context end");
		if (res != null) {
//			LOGGER.info("Context size = {}", bufferContext.size());
//			LOGGER.info("Freeing {} bytes", res.second());
			bufferContext.free(res.second());
			eventEmitter.emit("Buffer context clear");
			return res.first();
		}
		return null;
	}
}
