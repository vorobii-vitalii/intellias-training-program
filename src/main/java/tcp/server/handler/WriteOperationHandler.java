package tcp.server.handler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import tcp.server.ByteBufferPool;
import tcp.server.ConnectionImpl;
import tcp.server.MessageWriteRequest;
import tcp.server.ServerAttachment;
import tcp.server.SocketConnection;
import util.UnsafeConsumer;

import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class WriteOperationHandler implements Consumer<SelectionKey> {
	private static final int MAX_MSGS_WRITE = 10;
	public static final int NO_BYTES_WRITTEN = 0;
	private final Timer timer;
	private final Counter messagesWrittenCounter;
	private final BiConsumer<SelectionKey, Throwable> onError;
	private final ByteBufferPool byteBufferPool;
	private final Tracer writeHandlerTracer;

	public WriteOperationHandler(
			Timer timer,
			Counter messagesWrittenCounter,
			BiConsumer<SelectionKey, Throwable> onError,
			ByteBufferPool byteBufferPool,
			OpenTelemetry openTelemetry
	) {
		this.timer = timer;
		this.messagesWrittenCounter = messagesWrittenCounter;
		this.onError = onError;
		this.byteBufferPool = byteBufferPool;
		writeHandlerTracer = openTelemetry.getTracer("Write handler");
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		timer.record(() -> {
			var socketChannel = (SocketChannel) (selectionKey.channel());
			var attachmentObject = (ServerAttachment) (selectionKey.attachment());
			var responses = attachmentObject.responses();
			if (responses == null) {
				return;
			}
			try {
				var context = attachmentObject.getRequestSpan() == null
						? Context.current()
						: Context.current().with(attachmentObject.getRequestSpan());
				var span = writeHandlerTracer.spanBuilder("Write message").setParent(context).startSpan();
				ByteBuffer[] buffers = new ByteBuffer[MAX_MSGS_WRITE];
				List<Consumer<SocketConnection>> callbacks = new ArrayList<>(MAX_MSGS_WRITE);
				int bufferIndex = 0;
				for (int i = 0; i < MAX_MSGS_WRITE && attachmentObject.isWritable() && !responses.isEmpty(); i++) {
					var messageWriteRequest = responses.pollFirst();
					buffers[bufferIndex++] = messageWriteRequest.message();
					callbacks.add(messageWriteRequest.onWriteCallback());
				}
				long totalWritten = 0;
				while (true) {
					var written = socketChannel.write(buffers, 0, bufferIndex);
					if (written == NO_BYTES_WRITTEN) {
						break;
					}
					totalWritten += written;
				}
				span.addEvent("Written bytes", Attributes.of(AttributeKey.longKey("bytesWritten"), totalWritten));
				var connection = new ConnectionImpl(attachmentObject);
				for (int i = bufferIndex - 1; i >= 0; i--) {
					final Consumer<SocketConnection> callback = callbacks.get(i);
					if (buffers[i].hasRemaining()) {
						responses.addFirst(new MessageWriteRequest(buffers[i], callback));
					}
					else {
						byteBufferPool.save(buffers[i]);
						messagesWrittenCounter.increment();
						if (callback != null) {
							callback.accept(connection);
						}
					}
				}
				span.end();
				if (responses.isEmpty()) {
					selectionKey.interestOps(SelectionKey.OP_READ);
				}
			} catch (CancelledKeyException cancelledKeyException) {
				// Ignore
			} catch (Throwable e) {
				onError.accept(selectionKey, e);
			}
		});
	}

}
