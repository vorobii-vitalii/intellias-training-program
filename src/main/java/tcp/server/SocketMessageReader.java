package tcp.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import tcp.server.reader.MessageReader;
import tcp.server.reader.exception.ParseException;

public class SocketMessageReader<Message> {
	private final MessageReader<Message> messageReader;
	private final Timer messageReadTimer;

	public SocketMessageReader(MessageReader<Message> messageReader, Timer messageReadTimer) {
		this.messageReader = messageReader;
		this.messageReadTimer = messageReadTimer;
	}

	public Message readMessage(BufferContext bufferContext, ReadableByteChannel readableByteChannel, Span span)
			throws IOException, ParseException {
		var startTime = System.nanoTime();
		try {
			while (tryRead(readableByteChannel, bufferContext, span)) {
			}
			span.addEvent("Read from context");
			var res = messageReader.read(bufferContext, span::addEvent);
			span.addEvent("Read from context end");
			if (res != null) {
				bufferContext.free(res.second());
				span.addEvent("Buffer context clear");
				return res.first();
			}
			while (tryRead(readableByteChannel, bufferContext, span));
			return null;
		} finally {
			messageReadTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
		}
	}

	private boolean tryRead(ReadableByteChannel readableByteChannel, BufferContext bufferContext, Span span)
			throws IOException {
		var buffer = bufferContext.getAvailableBuffer();
		span.addEvent("Got buffer to write");
		return readableByteChannel.read(buffer) > 0;
	}

}
