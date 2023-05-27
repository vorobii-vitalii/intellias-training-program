package tcp.server;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Deque;
import java.util.Map;

import io.opentelemetry.api.trace.Span;
import net.jcip.annotations.NotThreadSafe;

@NotThreadSafe
public interface ServerAttachment {
	Channel getChannel();
	SocketConnection toSocketConnection();

	ByteBuffer allocate(int bytes);

	Span getRequestSpan();

	void writeToClientBuffer(byte[] bytes);

	void freeClientContext();

	InputStream getClientBufferInputStream();

	void setProtocol(String protocol);

	String protocol();

	BufferContext bufferContext();

	Deque<MessageWriteRequest> responses();

	Map<String, Object> context();

	boolean isWritable();

	boolean isReadable();

	SelectionKey getSelectionKey();

	void setSelectionKey(SelectionKey selectionKey);
}
