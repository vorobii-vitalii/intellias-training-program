package ws;

import server.ServerOperationType;
import server.TCPServer;
import server.TCPServerConfig;

import java.net.StandardProtocolFamily;
import java.nio.channels.spi.SelectorProvider;
import java.util.Map;


public class WebSocketServer {
	private static final int BUFFER_CAPACITY = 1000;
	private static final int PORT = 8000;
	private static final String HOSTNAME = "127.0.0.1";


	public static void main(String[] args) {
		var server = new TCPServer(
      TCPServerConfig.builder()
        .setHost(HOSTNAME)
        .setPort(PORT)
        .setProtocolFamily(StandardProtocolFamily.INET)
        .build(),
      SelectorProvider.provider(),
      System.err::println,
      Map.of(
        ServerOperationType.ACCEPT, new WebSocketAcceptOperationHandler(BUFFER_CAPACITY),
        ServerOperationType.READ, selectionKey -> {

        },
        ServerOperationType.WRITE, selectionKey -> {

        }
      ));

		server.start();
	}

}
