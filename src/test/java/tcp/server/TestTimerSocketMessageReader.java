package tcp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.Timer;

@ExtendWith(MockitoExtension.class)
class TestTimerSocketMessageReader {
	private static final int MESSAGE = 232;

	@Mock
	BufferContext bufferContext;

	@Mock
	Channel channel;

	@Mock
	EventEmitter eventEmitter;

	@Mock
	SocketMessageReader<Integer> socketMessageReader;

	@Mock
	Timer timer;

	@InjectMocks
	TimerSocketMessageReader<Integer> timerSocketMessageReader;

	@Test
	void readMessage() throws IOException {
		when(socketMessageReader.readMessage(bufferContext, channel, eventEmitter))
				.thenReturn(MESSAGE);
		assertThat(timerSocketMessageReader.readMessage(bufferContext, channel, eventEmitter)).isEqualTo(MESSAGE);
		verify(timer).record(anyLong(), eq(TimeUnit.NANOSECONDS));
	}
}