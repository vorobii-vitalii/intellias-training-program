package tcp.server;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import tcp.server.handler.GenericReadOperationHandler;
import tcp.server.reader.exception.ParseException;
import tcp.server.reader.MessageReader;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class SocketMessageReader<Message> {
	private static final Logger LOGGER = LoggerFactory.getLogger(GenericReadOperationHandler.class);

	private final MessageReader<Message> messageReader;
	private final Timer messageReadTimer;
	private final Counter successReads;
	private final Counter notSuccessReads;

	public SocketMessageReader(
			MessageReader<Message> messageReader,
			Timer messageReadTimer,
			Counter successReads,
			Counter notSuccessReads
	) {
		this.messageReader = messageReader;
		this.messageReadTimer = messageReadTimer;
		this.successReads = successReads;
		this.notSuccessReads = notSuccessReads;
	}

	public Message readMessage(BufferContext bufferContext, ReadableByteChannel readableByteChannel)
					throws IOException, ParseException {
		var startTime = System.nanoTime();
		var buffer = bufferContext.getAvailableBuffer();
		try {
			do {
				var res = messageReader.read(bufferContext);
				if (res != null) {
//					LOGGER.info("Read message {}, bufferSize = {}", res, bufferContext.size());
					bufferContext.free(res.second());
//					LOGGER.info("Buffer size = {}", bufferContext.size());
					successReads.increment();
					return res.first();
				}
			}
			while (readableByteChannel.read(buffer) > 0);
			notSuccessReads.increment();
			return null;
		}
		finally {
			messageReadTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
		}

	}

}
