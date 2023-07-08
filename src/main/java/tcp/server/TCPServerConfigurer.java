package tcp.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.SelectorProvider;

public class TCPServerConfigurer implements ServerConfigurer {
	private static final int BACKLOG = 10_000;

	@Override
	public AbstractSelectableChannel configureServerChannel(SelectorProvider selectorProvider, ServerConfig serverConfig) throws IOException {
		var serverSocketChannel = selectorProvider.openServerSocketChannel(serverConfig.getProtocolFamily());
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.bind(new InetSocketAddress(serverConfig.getHost(), serverConfig.getPort()), BACKLOG);
		return serverSocketChannel;
	}
}
