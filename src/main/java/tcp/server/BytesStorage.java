package tcp.server;

import java.io.InputStream;

public interface BytesStorage {
	void appendBytesToContext(byte[] data);
	void freeContext();
	InputStream getContextInputStream();
}
