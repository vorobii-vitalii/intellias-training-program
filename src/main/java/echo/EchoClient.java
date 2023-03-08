package echo;

import server.TCPClient;
import server.TCPClientConfig;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;

public class EchoClient {
	private static final int BUFFER_SIZE = 250;

	public static void main(String[] args) throws IOException {
		var message = "Hello!";

		var bufferToSend = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));

		var tcpClientConfig = TCPClientConfig.builder()
				.setHost(System.getenv("SERVER_HOST"))
				.setPort(Integer.parseInt(System.getenv("SERVER_PORT")))
				.setProtocolFamily(StandardProtocolFamily.INET)
				.setBufferSize(BUFFER_SIZE)
				.build();

		try (var tcpClient = new TCPClient(tcpClientConfig, SelectorProvider.provider())) {
			var receiveBuffer = ByteBuffer.allocateDirect(bufferToSend.capacity());
			tcpClient.connect();
			tcpClient.write(bufferToSend);

			tcpClient.read(buffer -> {
				receiveBuffer.put(buffer);
				return receiveBuffer.position() != receiveBuffer.capacity();
			});
			System.out.println("Written = " + message + " received = " + bufferToString(receiveBuffer));
		}
	}

	private static String bufferToString(ByteBuffer buffer) {
		byte[] bytes = new byte[buffer.capacity()];
		buffer.flip();
		buffer.get(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

}
