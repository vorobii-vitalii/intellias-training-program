package tcp.server.reader;

import tcp.server.EventEmitter;
import tcp.server.reader.exception.ParseException;
import tcp.server.BufferContext;
import util.Pair;

public interface MessageReader<MessageObject> {
	/**
	 * Reads message from BufferContext
	 * @param bufferContext - Buffer context
	 * @return (messageObject, numBytes) if enough bytes are present in buffer, null otherwise
	 * @throws ParseException - when message has incorrect structure
	 */
	Pair<MessageObject, Integer> read(BufferContext bufferContext, EventEmitter eventEmitter) throws ParseException;
}
