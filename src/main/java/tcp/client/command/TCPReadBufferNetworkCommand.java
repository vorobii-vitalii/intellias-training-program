package tcp.client.command;

import tcp.client.TCPConnection;

import java.io.IOException;
import java.nio.ByteBuffer;

public class TCPReadBufferNetworkCommand implements NetworkCommand<ByteBuffer> {
	private final TCPConnection tcpConnection;
	private final ByteBuffer buffer;

	public TCPReadBufferNetworkCommand(TCPConnection tcpConnection, int bufferSize) {
		this.tcpConnection = tcpConnection;
		this.buffer = ByteBuffer.allocate(bufferSize);
	}

	@Override
	public ByteBuffer execute() throws IOException {
		while (!Thread.currentThread().isInterrupted() && buffer.position() != buffer.limit()) {
			tcpConnection.socketChannel().read(buffer);
		}
		return buffer;
	}
}
