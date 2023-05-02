package tcp.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import net.jcip.annotations.ThreadSafe;
import util.Serializable;

@ThreadSafe
public class ConnectionImpl implements SocketConnection {
	public static final int NUM_RETRIES = 5;
	private final SelectionKey selectionKey;

	public ConnectionImpl(SelectionKey selectionKey) {
		this.selectionKey = selectionKey;
	}

	@Override
	public void appendBytesToContext(byte[] data) {
		for (byte b : data) {
			getServerAttachment().getClientBufferContext().getAvailableBuffer().put(b);
		}
	}

	@Override
	public void freeContext() {
		getServerAttachment().getClientBufferContext().free(getServerAttachment().getClientBufferContext().size());
	}

	@Override
	public int getContextLength() {
		return getServerAttachment().getClientBufferContext().size();
	}

	@Override
	public byte getByteFromContext(int index) {
		return getServerAttachment().getClientBufferContext().get(index);
	}

	@Override
	public void setProtocol(String protocol) {
		getServerAttachment().setProtocol(protocol);
	}

	@Override
	public void changeOperation(int operation) {
		selectionKey.interestOps(operation);
		selectionKey.selector().wakeup();
	}

	@Override
	public void appendResponse(Serializable response) {
		// Issue
		if (!selectionKey.isValid()) {
			throw new CancelledKeyException();
		}
		try {
			var message = ByteBuffer.wrap(response.serialize());
			getServerAttachment()
					.responses()
					.put(message);
		}
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int getResponsesSize() {
		return getServerAttachment().responses().size();
	}


	@Override
	public void setMetadata(String key, Object value) {
		getServerAttachment().context().put(key, value);
	}

	@Override
	public String getMetadata(String key) {
		return (String) getServerAttachment().context().get(key);
	}

	@Override
	public SocketAddress getAddress() {
		try {
			return ((SocketChannel) selectionKey.channel()).getRemoteAddress();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void close() {
		getServerAttachment().invalidate();
		selectionKey.cancel();
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
		return "Connection[" + getServerAttachment() + " " + getAddress() + "]";
	}

	private ServerAttachment getServerAttachment() {
		return (ServerAttachment) selectionKey.attachment();
	}

}
