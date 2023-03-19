package tcp.client.command;

import tcp.client.TCPConnection;

import java.io.IOException;
import java.nio.ByteBuffer;

public class TCPWriteBufferNetworkCommand implements NetworkCommand<Void> {
	private final TCPConnection tcpConnection;
	private final ByteBuffer bufferToWrite;

	public TCPWriteBufferNetworkCommand(TCPConnection tcpConnection, ByteBuffer bufferToWrite) {
		this.tcpConnection = tcpConnection;
		this.bufferToWrite = bufferToWrite;
	}

	@Override
	public Void execute() throws IOException {
		while (bufferToWrite.hasRemaining()) {
			tcpConnection.getSocketChannel().write(bufferToWrite);
		}
		return null;
	}
}
