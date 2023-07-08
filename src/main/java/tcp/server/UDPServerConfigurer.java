package tcp.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.SelectorProvider;

public class UDPServerConfigurer implements ServerConfigurer {
	@Override
	public AbstractSelectableChannel configureServerChannel(SelectorProvider selectorProvider, ServerConfig serverConfig) throws IOException {
		var datagramChannel = selectorProvider.openDatagramChannel(serverConfig.getProtocolFamily());
		datagramChannel.configureBlocking(false);
		datagramChannel.bind(new InetSocketAddress(serverConfig.getHost(), serverConfig.getPort()));
		return datagramChannel;
	}
}
