package tcp.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

import net.jcip.annotations.ThreadSafe;

@ThreadSafe
public class ConnectionImpl implements SocketConnection {
	private final ServerAttachment serverAttachment;

	public ConnectionImpl(ServerAttachment serverAttachment) {
		this.serverAttachment = serverAttachment;
	}

	@Override
	public boolean isClosed() {
		return !serverAttachment.getChannel().isOpen();
	}

	@Override
	public void changeOperation(OperationType operationType) {
		serverAttachment.changeInterestedOperation(operationType);
	}

	@Override
	public void appendBytesToContext(byte[] data) {
		serverAttachment.writeToClientBuffer(data);
	}

	@Override
	public void freeContext() {
		serverAttachment.freeClientContext();
	}

	@Override
	public InputStream getContextInputStream() {
		return serverAttachment.getClientBufferInputStream();
	}

	@Override
	public void setProtocol(String protocol) {
		serverAttachment.setProtocol(protocol);
	}

	@Override
	public void appendResponse(ByteBuffer buffer, Consumer<SocketConnection> onWriteCallback) {
		if (isClosed()) {
			return;
		}
		serverAttachment
				.responses()
				.add(new MessageWriteRequest(buffer, onWriteCallback));
	}

	@Override
	public void setMetadata(String key, Object value) {
		serverAttachment.connectionMetadata().put(key, value);
	}

	@Override
	public String getMetadata(String key) {
		return (String) serverAttachment.connectionMetadata().get(key);
	}

	@Override
	public void close() throws IOException {
		if (isClosed()) {
			return;
		}
		serverAttachment.getChannel().close();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ConnectionImpl that = (ConnectionImpl) o;
		return Objects.equals(serverAttachment, that.serverAttachment);
	}

	@Override
	public int hashCode() {
		return Objects.hash(serverAttachment);
	}

	@Override
	public String toString() {
		return "Connection[" + serverAttachment + "]";
	}

}
