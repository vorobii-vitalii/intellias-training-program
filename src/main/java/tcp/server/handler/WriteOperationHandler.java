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
import tcp.server.ServerAttachment;
import util.UnsafeConsumer;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class WriteOperationHandler implements Consumer<SelectionKey> {
	private static final int MAX_MSGS_WRITE = 1;
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
			var socketChannel = (WritableByteChannel) (selectionKey.channel());
			var attachmentObject = (ServerAttachment) (selectionKey.attachment());
			var responses = attachmentObject.responses();
			if (responses == null) {
				return;
			}
			try {
				for (int i = 0; i < MAX_MSGS_WRITE && selectionKey.isWritable() && attachmentObject.isWritable() && !responses.isEmpty(); i++) {
					var writeRequest = responses.peek();
					var buffer = writeRequest.message();
					var context = Context.current();
					if (writeRequest.parentSpan() != null) {
						context = context.with(writeRequest.parentSpan());
					}
					var span = writeHandlerTracer.spanBuilder("Write message").setParent(context).startSpan();
					boolean canAcceptMore = true;
					long totalBytesWritten = 0;
					while (buffer.hasRemaining()) {
						var bytesWritten = socketChannel.write(buffer);
						totalBytesWritten += bytesWritten;
						if (bytesWritten == NO_BYTES_WRITTEN) {
							canAcceptMore = false;
							break;
						}
					}
					span.addEvent("Written bytes", Attributes.of(AttributeKey.longKey("bytesWritten"), totalBytesWritten));
					span.end();
					if (!canAcceptMore) {
						break;
					}
					byteBufferPool.save(buffer);
					if (writeRequest.onWriteResponseCallback() != null) {
						writeRequest.onWriteResponseCallback().accept(selectionKey);
					}
					responses.poll();
					messagesWrittenCounter.increment();
				}
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
