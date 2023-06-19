package tcp.server;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Channel {
	int read(ByteBuffer byteBuffer) throws IOException;
	int write(ByteBuffer buffer) throws IOException;
	boolean isOpen();
	void close() throws IOException;
}
