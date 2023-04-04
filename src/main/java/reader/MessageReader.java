package reader;

import exception.ParseException;
import tcp.server.ReadBufferContext;

public interface MessageReader<MessageObject> {
	MessageObject read(ReadBufferContext readBufferContext) throws ParseException;
}
