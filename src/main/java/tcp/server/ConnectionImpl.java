package tcp.server;

import static java.nio.channels.SelectionKey.OP_READ;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.function.Consumer;

import io.opentelemetry.api.trace.Span;
import net.jcip.annotations.ThreadSafe;
import util.Serializable;
import util.UnsafeConsumer;

@ThreadSafe
public class ConnectionImpl implements SocketConnection {
	public static final int EOF = -1;
	private final ServerAttachment serverAttachment;

	public ConnectionImpl(ServerAttachment serverAttachment) {
		this.serverAttachment = serverAttachment;
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
	public void changeOperation(int operation) {
		serverAttachment.getSelectionKey().interestOps(operation);
		serverAttachment.getSelectionKey().selector().wakeup();
	}

	@Override
	public void appendResponse(Serializable response) {
		appendResponse(response, null, null);
	}

	@Override
	public void appendResponse(Serializable response, EventEmitter eventEmitter, Consumer<SocketConnection> onWriteCallback) {
		// Issue
		if (!serverAttachment.getSelectionKey().isValid()) {
			throw new CancelledKeyException();
		}
		ByteBuffer message = serverAttachment.allocate(response.getSize());
		if (eventEmitter != null) {
			eventEmitter.emit("Allocated buffer");
		}
		response.serialize(message);
		if (eventEmitter != null) {
			eventEmitter.emit("Serialized");
		}
		message.flip();
		serverAttachment
				.responses()
				.add(new MessageWriteRequest(message, onWriteCallback));
		if (eventEmitter != null) {
			eventEmitter.emit("Written to queue");
		}
	}

	@Override
	public void appendResponse(ByteBuffer buffer, Consumer<SocketConnection> onWriteCallback) {
		if (!serverAttachment.getSelectionKey().isValid()) {
			throw new CancelledKeyException();
		}
		serverAttachment
				.responses()
				.add(new MessageWriteRequest(buffer, onWriteCallback));
	}

	@Override
	public void setMetadata(String key, Object value) {
		serverAttachment.context().put(key, value);
	}

	@Override
	public String getMetadata(String key) {
		return (String) serverAttachment.context().get(key);
	}

	@Override
	public void close() {
		try {
			serverAttachment.getSelectionKey().channel().close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void changeSelector(Selector selector) {
		try {
			var oldSelectionKey = serverAttachment.getSelectionKey();
			var channel = oldSelectionKey.channel();
			var newSelectionKey = channel.register(selector, OP_READ, serverAttachment);
			serverAttachment.setSelectionKey(newSelectionKey);
			selector.wakeup();
			oldSelectionKey.cancel();
		}
		catch (ClosedChannelException e) {
			throw new RuntimeException(e);
		}
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
