package tcp.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
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
		for (byte b : data) {
			serverAttachment.getClientBufferContext().getAvailableBuffer().put(b);
		}
	}

	@Override
	public Span getConnectionSpan() {
		return serverAttachment.getRequestSpan();
	}

	@Override
	public void freeContext() {
		serverAttachment.getClientBufferContext().free(serverAttachment.getClientBufferContext().size());
	}

	@Override
	public int getContextLength() {
		return serverAttachment.getClientBufferContext().size();
	}

	@Override
	public byte getByteFromContext(int index) {
		return serverAttachment.getClientBufferContext().get(index);
	}

	@Override
	public InputStream getContextInputStream() {
		return new InputStream() {
			private int index = 0;

			@Override
			public int read() {
				var size = getContextLength();
				if (index == size) {
					return EOF;
				}
				return getByteFromContext(index++);
			}
		};
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
	public void appendResponse(Serializable response, Span writeRequestSpan, UnsafeConsumer<SelectionKey> onWriteResponseCallback) {
		// Issue
		if (!serverAttachment.getSelectionKey().isValid()) {
			throw new CancelledKeyException();
		}
		ByteBuffer message = serverAttachment.allocate(response.getSize());
		if (writeRequestSpan != null) {
			writeRequestSpan.addEvent("Allocated buffer");
		}
		response.serialize(message);
		if (writeRequestSpan != null) {
			writeRequestSpan.addEvent("Serialized");
		}
		message.flip();
		serverAttachment
				.responses()
				.add(new MessageWriteRequest(message, onWriteResponseCallback));
		if (writeRequestSpan != null) {
			writeRequestSpan.addEvent("Written to queue");
		}
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
	public SocketAddress getAddress() {
		try {
			return ((SocketChannel) (serverAttachment.getSelectionKey().channel())).getRemoteAddress();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void close() {
		try {
			serverAttachment.getSelectionKey().channel().close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof SocketConnection r)) return false;
		return this.getAddress().equals(r.getAddress());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getAddress());
	}

	@Override
	public String toString() {
		return "Connection[" + serverAttachment + " " + getAddress() + "]";
	}



}
