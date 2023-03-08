package echo;

import server.ServerOperationType;
import server.TCPServer;
import server.TCPServerConfig;

import java.net.StandardProtocolFamily;
import java.nio.channels.spi.SelectorProvider;
import java.util.Map;

public class EchoServer {
    private static final int BUFFER_CAPACITY = 1000;

    public static void main(String[] args) {
        var serverConfig = TCPServerConfig.builder()
                .setHost(System.getenv("SERVER_HOST"))
                .setPort(Integer.parseInt(System.getenv("SERVER_PORT")))
                .setProtocolFamily(StandardProtocolFamily.INET)
                .build();
        var operationHandlerByType = Map.of(
                ServerOperationType.ACCEPT, new EchoAcceptOperationHandler(BUFFER_CAPACITY),
                ServerOperationType.READ, new EchoReadOperationHandler(),
                ServerOperationType.WRITE, new EchoWriteOperationHandler()
        );
        var server = new TCPServer(serverConfig, SelectorProvider.provider(), System.err::println, operationHandlerByType);
        server.start();
    }

}
