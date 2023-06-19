package tcp.server;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Timer;
import tcp.server.reader.exception.ParseException;

public class TimerSocketMessageReader<Message> implements SocketMessageReader<Message> {
	private final Timer messageReadTimer;
	private final SocketMessageReader<Message> messageReader;

	public TimerSocketMessageReader(Timer messageReadTimer, SocketMessageReader<Message> messageReader) {
		this.messageReadTimer = messageReadTimer;
		this.messageReader = messageReader;
	}

	@Override
	public Message readMessage(BufferContext bufferContext, Channel channel, EventEmitter eventEmitter) throws IOException, ParseException {
		var startTime = System.nanoTime();
		try {
			return messageReader.readMessage(bufferContext, channel, eventEmitter);
		}
		finally {
			messageReadTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
		}
	}
}
