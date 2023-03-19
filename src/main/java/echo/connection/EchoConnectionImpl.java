package echo.connection;

import tcp.client.TCPConnection;
import tcp.client.command.TCPReadBufferNetworkCommand;
import tcp.client.command.TCPWriteBufferNetworkCommand;

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
		new TCPWriteBufferNetworkCommand(tcpConnection, bufferToSend).execute();
		var receiveBuffer = new TCPReadBufferNetworkCommand(tcpConnection, bufferToSend.capacity()).execute();
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
