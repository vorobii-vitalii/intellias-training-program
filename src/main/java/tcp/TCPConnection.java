package tcp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Function;

public interface TCPConnection extends AutoCloseable {
	/**
	 * Writes to socket
	 * @param buffer - buffer to write
	 */
	void write(ByteBuffer buffer) throws IOException;

	/**
	 * Read from socket
	 * @param function - accepts chunk of data read from socket
	 * and returns true if more data is needed from socket, false otherwise
	 */
	void read(Function<ByteBuffer, Boolean> function) throws IOException;
}
