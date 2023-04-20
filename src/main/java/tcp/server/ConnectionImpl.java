package tcp.server;

import net.jcip.annotations.ThreadSafe;
import util.Serializable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;

@ThreadSafe
public class ConnectionImpl implements SocketConnection {
	private final SelectionKey selectionKey;

	public ConnectionImpl(SelectionKey selectionKey) {
		this.selectionKey = selectionKey;
	}

	@Override
	public void setProtocol(String protocol) {
		getServerAttachment().setProtocol(protocol);
	}

	@Override
	public void changeOperation(int operation) {
		selectionKey.interestOps(operation);
//		selectionKey.selector().wakeup();
	}

	@Override
	public void appendResponse(Serializable response) {
		getServerAttachment().responses().add(response);
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
		return Objects.hash(selectionKey);
	}

	@Override
	public String toString() {
		return "Connection[" + getServerAttachment() + " " + getAddress() + "]";
	}

	private ServerAttachment getServerAttachment() {
		return (ServerAttachment) selectionKey.attachment();
	}
}
