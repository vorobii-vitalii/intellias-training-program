package tcp;

import java.io.IOException;

public interface TCPClient {
	TCPConnection createConnection() throws IOException;
}
