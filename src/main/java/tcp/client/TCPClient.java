package tcp.client;

import java.io.IOException;

public interface TCPClient {
	TCPConnection createConnection() throws IOException;
}
