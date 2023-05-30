package tcp.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.trace.Span;
import tcp.server.impl.NIOChannel;

public class ServerAttachmentImpl implements ServerAttachment {
	private final Map<OperationType, Integer> CODE_BY_OPERATION_TYPE = new EnumMap<>(Map.of(
			OperationType.READ, SelectionKey.OP_READ,
			OperationType.WRITE, SelectionKey.OP_WRITE
	));

	private final BufferContext bufferContext;
	private final Deque<MessageWriteRequest> responses;
	private final Map<String, Object> context;
	private final Span requestSpan;
	private volatile String protocol;
	private volatile SelectionKey selectionKey;
	private final List<byte[]> bytesArray = new ArrayList<>();

	public ServerAttachmentImpl(
			String protocol,
			BufferContext bufferContext,
			Deque<MessageWriteRequest> responses,
			Map<String, Object> context,
			Span requestSpan,
			SelectionKey selectionKey
	) {
		this.protocol = protocol;
		this.bufferContext = bufferContext;
		this.responses = responses;
		this.context = context;
		this.requestSpan = requestSpan;
		this.selectionKey = selectionKey;
	}

	@Override
	public Channel getChannel() {
		return new NIOChannel((SocketChannel) selectionKey.channel());
	}

	@Override
	public SocketConnection toSocketConnection() {
		return new ConnectionImpl(this);
	}

	@Override
	public Span getRequestSpan() {
		return requestSpan;
	}

	@Override
	public void writeToClientBuffer(byte[] bytes) {
		bytesArray.add(bytes);
	}

	@Override
	public void freeClientContext() {
		bytesArray.clear();
	}

	@Override
	public InputStream getClientBufferInputStream() {
		return new CompositeInputStream(
				bytesArray.stream()
						.map(ByteArrayInputStream::new)
						.toArray(InputStream[]::new));
	}

	@Override
	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	@Override
	public String protocol() {
		return protocol;
	}

	@Override
	public BufferContext bufferContext() {
		return bufferContext;
	}

	@Override
	public Deque<MessageWriteRequest> responses() {
		return responses;
	}

	@Override
	public Map<String, Object> connectionMetadata() {
		return context;
	}

	@Override
	public String toString() {
		try {
			var channel = (SocketChannel) selectionKey.channel();
			return "ServerAttachment[" +
					(channel.isOpen() ? channel.getRemoteAddress() : "CLOSED")
					+ ']';
		}
		catch (IOException e) {
			return "Connection closed...";
		}
	}

	@Override
	public boolean isWritable() {
		return selectionKey.isWritable();
	}

	@Override
	public boolean isReadable() {
		return selectionKey.isReadable();
	}

	@Override
	public void changeInterestedOperation(OperationType operationType) {
		selectionKey.interestOps(CODE_BY_OPERATION_TYPE.get(operationType));
	}

	public void setSelectionKey(SelectionKey selectionKey) {
		this.selectionKey = selectionKey;
	}
}
