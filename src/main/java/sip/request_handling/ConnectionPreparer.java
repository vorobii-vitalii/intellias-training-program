package sip.request_handling;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public interface ConnectionPreparer {
	SelectionKey prepareConnection(SocketChannel socketChannel) throws IOException;
}
