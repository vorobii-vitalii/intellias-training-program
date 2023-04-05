package reader;

import exception.ParseException;
import tcp.server.BufferContext;

public interface MessageReader<MessageObject> {
	MessageObject read(BufferContext bufferContext) throws ParseException;
}
