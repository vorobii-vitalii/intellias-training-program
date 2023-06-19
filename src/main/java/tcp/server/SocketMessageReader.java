package tcp.server;

import java.io.IOException;

import tcp.server.reader.exception.ParseException;

public interface SocketMessageReader<Message> {
	Message readMessage(BufferContext bufferContext, Channel channel, EventEmitter eventEmitter)
			throws IOException, ParseException;
}
