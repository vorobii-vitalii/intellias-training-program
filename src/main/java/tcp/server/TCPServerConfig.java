package tcp.server;

import java.net.ProtocolFamily;
import java.nio.channels.SelectionKey;
import java.util.function.Consumer;

public final class TCPServerConfig {
	private String host;
	private int port;
	private ProtocolFamily protocolFamily;
	private Consumer<SelectionKey> onConnectionClose = key -> {};

	private TCPServerConfig() {
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public ProtocolFamily getProtocolFamily() {
		return protocolFamily;
	}

	public static Builder builder() {
		return new Builder();
	}

	public Consumer<SelectionKey> getOnConnectionClose() {
		return onConnectionClose;
	}

	public static class Builder {
		private final TCPServerConfig config = new TCPServerConfig();

		private Builder() {
		}

		public Builder setHost(String host) {
			config.host = host;
			return this;
		}

		public Builder setPort(int port) {
			config.port = port;
			return this;
		}

		public Builder setProtocolFamily(ProtocolFamily protocolFamily) {
			config.protocolFamily = protocolFamily;
			return this;
		}

		public Builder onConnectionClose(Consumer<SelectionKey> onConnectionClose) {
			config.onConnectionClose = onConnectionClose;
			return this;
		}

		public TCPServerConfig build() {
			return config;
		}
	}

}
