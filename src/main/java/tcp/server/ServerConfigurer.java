package tcp.server;

import java.io.IOException;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.SelectorProvider;

@FunctionalInterface
public interface ServerConfigurer {
	AbstractSelectableChannel configureServerChannel(SelectorProvider selectorProvider, ServerConfig serverConfig) throws IOException;
}
