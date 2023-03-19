package tcp.client;

import java.nio.channels.SocketChannel;

public interface TCPConnection extends AutoCloseable {
	SocketChannel getSocketChannel();

}
