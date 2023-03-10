package echo;

import tcp.TCPConnection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class EchoConnectionImpl implements EchoConnection {
	private final TCPConnection tcpConnection;

	public EchoConnectionImpl(TCPConnection tcpConnection) {
		this.tcpConnection = tcpConnection;
	}

	@Override
	public String sendMessage(String message) throws IOException {
		var bufferToSend = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
		tcpConnection.write(bufferToSend);
		var receiveBuffer = ByteBuffer.allocate(bufferToSend.capacity());
		tcpConnection.read(buffer -> {
			receiveBuffer.put(buffer);
			return receiveBuffer.position() < receiveBuffer.capacity();
		});
		return bufferToString(receiveBuffer);
	}

	@Override
	public void close() throws Exception {
		tcpConnection.close();
	}

	private String bufferToString(ByteBuffer buffer) {
		var bytes = new byte[buffer.limit()];
		buffer.flip();
		buffer.get(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

}
