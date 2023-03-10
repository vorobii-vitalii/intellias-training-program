package tcp;

import java.net.ProtocolFamily;

public final class TCPServerConfig {
	private String host;
	private int port;
	private ProtocolFamily protocolFamily;

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

		public TCPServerConfig build() {
			return config;
		}
	}

}
