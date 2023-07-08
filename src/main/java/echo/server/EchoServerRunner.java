package echo.server;

import tcp.server.ServerConfig;

import java.net.StandardProtocolFamily;
import java.nio.channels.spi.SelectorProvider;

public class EchoServerRunner {
    private static final int BUFFER_CAPACITY = 1000;

    public static void main(String[] args) {
        var serverConfig = ServerConfig.builder()
                .setHost(System.getenv("SERVER_HOST"))
                .setPort(Integer.parseInt(System.getenv("SERVER_PORT")))
                .setProtocolFamily(StandardProtocolFamily.INET)
                .build();
        new EchoServer(serverConfig, SelectorProvider.provider(), System.err::println, BUFFER_CAPACITY).start();
    }

}
