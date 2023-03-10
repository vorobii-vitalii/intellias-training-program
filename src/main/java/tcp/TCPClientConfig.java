package tcp;

import java.net.ProtocolFamily;

public class TCPClientConfig {
	private String host;
	private int port;
	private ProtocolFamily protocolFamily;
	private int bufferSize;
	private int numRetries = 1;
	private int waitBeforeAttemptsMs = 50;

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

	public int getNumRetries() {
		return numRetries;
	}

	public int getWaitBeforeAttemptsInMilliseconds() {
		return waitBeforeAttemptsMs;
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

		public TCPClientConfig.Builder setNumRetries(int numRetries) {
			config.numRetries = numRetries;
			return this;
		}

		public TCPClientConfig.Builder setWaitBeforeAttemptsInMilliseconds(int waitBeforeAttemptsInMilliseconds) {
			config.waitBeforeAttemptsMs = waitBeforeAttemptsInMilliseconds;
			return this;
		}

		public TCPClientConfig build() {
			return config;
		}
	}
}
