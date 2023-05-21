package tcp.server;

import io.opentelemetry.api.trace.Span;
import token_bucket.TokenBucket;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

public final class ServerAttachment {
	private final TokenBucket<SocketAddress> writeTokenBucket;
	private final BufferContext bufferContext;
	private final Deque<MessageWriteRequest> responses;
	private final Map<String, Object> context;
	private final BufferContext clientBufferContext;
	private final TokenBucket<SocketAddress> readTokenBucket;
	private final Span requestSpan;
	private final ByteBufferPool byteBufferPool;
	private volatile String protocol;
	private volatile SelectionKey selectionKey;

	public ServerAttachment(
			String protocol,
			BufferContext bufferContext,
			BufferContext clientBufferContext,
			Deque<MessageWriteRequest> responses,
			Map<String, Object> context,
			TokenBucket<SocketAddress> writeTokenBucket,
			TokenBucket<SocketAddress> readTokenBucket,
			Span requestSpan,
			ByteBufferPool byteBufferPool,
			SelectionKey selectionKey
	) {
		this.protocol = protocol;
		this.bufferContext = bufferContext;
		this.clientBufferContext = clientBufferContext;
		this.responses = responses;
		this.context = context;
		this.writeTokenBucket = writeTokenBucket;
		this.readTokenBucket = readTokenBucket;
		this.requestSpan = requestSpan;
		this.byteBufferPool = byteBufferPool;
		this.selectionKey = selectionKey;
	}

	public ByteBuffer allocate(int bytes) {
		return byteBufferPool.allocate(bytes);
	}

	public Span getRequestSpan() {
		return requestSpan;
	}

	public TokenBucket<SocketAddress> getWriteTokenBucket() {
		return writeTokenBucket;
	}

	public TokenBucket<SocketAddress> getReadTokenBucket() {
		return readTokenBucket;
	}

	public BufferContext getClientBufferContext() {
		return clientBufferContext;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public String protocol() {
		return protocol;
	}

	public BufferContext bufferContext() {
		return bufferContext;
	}

	public Deque<MessageWriteRequest> responses() {
		return responses;
	}

	public Map<String, Object> context() {
		return context;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		var that = (ServerAttachment) obj;
		return Objects.equals(this.protocol, that.protocol) &&
				Objects.equals(this.bufferContext, that.bufferContext) &&
				Objects.equals(this.responses, that.responses) &&
				Objects.equals(this.context, that.context);
	}

	@Override
	public int hashCode() {
		return Objects.hash(protocol, bufferContext, responses, context);
	}

	@Override
	public String toString() {
		return "ServerAttachment[" +
				"protocol=" + protocol + ", " +
				+']';
	}

	public boolean isWritable() {
		return true;
//		return writeTokenBucket.takeToken();
	}

	public boolean isReadable() {
		return true;
//		return readTokenBucket.takeToken();
	}

	public SelectionKey getSelectionKey() {
		return selectionKey;
	}

	public void setSelectionKey(SelectionKey selectionKey) {
		this.selectionKey = selectionKey;
	}
}
