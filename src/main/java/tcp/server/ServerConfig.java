package tcp.server;

import java.net.ProtocolFamily;
import java.nio.channels.SelectionKey;
import java.util.function.Consumer;

public final class ServerConfig {
	private String name = "Undefined";
	private String host;
	private int port;
	private ProtocolFamily protocolFamily;
	private Consumer<SelectionKey> onConnectionClose = key -> {};
	private int serverInterestOps = SelectionKey.OP_ACCEPT;

	private ServerConfig() {
	}

	public String getName() {
		return name;
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

	public int getServerInterestOps() {
		return serverInterestOps;
	}

	public static Builder builder() {
		return new Builder();
	}

	public Consumer<SelectionKey> getOnConnectionClose() {
		return onConnectionClose;
	}

	public static class Builder {
		private final ServerConfig config = new ServerConfig();

		private Builder() {
		}

		public Builder setName(String name) {
			config.name = name;
			return this;
		}

		public Builder setHost(String host) {
			config.host = host;
			return this;
		}

		public Builder setPort(int port) {
			config.port = port;
			return this;
		}

		public Builder setInterestOps(int ops) {
			config.serverInterestOps = ops;
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

		public ServerConfig build() {
			return config;
		}
	}

}
