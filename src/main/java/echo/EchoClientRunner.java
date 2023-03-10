package echo;

import tcp.TCPClientConfig;

import java.net.StandardProtocolFamily;
import java.nio.channels.spi.SelectorProvider;

public class EchoClientRunner {
	private static final int BUFFER_SIZE = 250;
	private static final int NUM_RETRIES = 10;
	private static final int WAIT_BEFORE_ATTEMPTS_IN_MILLISECONDS = 1000;

	public static void main(String[] args) {
		var message = "Hello!";

		var tcpClientConfig = TCPClientConfig.builder()
						.setHost(System.getenv("SERVER_HOST"))
						.setPort(Integer.parseInt(System.getenv("SERVER_PORT")))
						.setProtocolFamily(StandardProtocolFamily.INET)
						.setBufferSize(BUFFER_SIZE)
						.setNumRetries(NUM_RETRIES)
						.setWaitBeforeAttemptsInMilliseconds(WAIT_BEFORE_ATTEMPTS_IN_MILLISECONDS)
						.build();

		var echoClient = new EchoClient(tcpClientConfig, SelectorProvider.provider());

		try (var connection = echoClient.createConnection()) {
			var reply = connection.sendMessage(message);
			System.out.println("Written = " + message + " received = " + reply);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
