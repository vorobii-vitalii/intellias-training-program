package tcp.client;

import java.net.ProtocolFamily;

public class TCPClientConfig {
	private String host;
	private int port;
	private ProtocolFamily protocolFamily;
	private int bufferSize;

	private TCPClientConfig() {
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public int getBufferSize() {
		return bufferSize;
	}


	public ProtocolFamily getProtocolFamily() {
		return protocolFamily;
	}

	public static TCPClientConfig.Builder builder() {
		return new TCPClientConfig.Builder();
	}

	public static class Builder {
		private final TCPClientConfig config = new TCPClientConfig();

		private Builder() {
		}

		public TCPClientConfig.Builder setHost(String host) {
			config.host = host;
			return this;
		}

		public TCPClientConfig.Builder setPort(int port) {
			config.port = port;
			return this;
		}

		public TCPClientConfig.Builder setBufferSize(int bufferSize) {
			config.bufferSize = bufferSize;
			return this;
		}

		public TCPClientConfig.Builder setProtocolFamily(ProtocolFamily protocolFamily) {
			config.protocolFamily = protocolFamily;
			return this;
		}

		public TCPClientConfig build() {
			return config;
		}
	}
}
