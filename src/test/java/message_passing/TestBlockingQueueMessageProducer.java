package message_passing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.jupiter.api.Test;

class TestBlockingQueueMessageProducer {
	private static final int EVENT = 1;

	BlockingQueue<Integer> blockingQueue = new LinkedBlockingQueue<>();
	BlockingQueueMessageProducer<Integer> queueMessageProducer = new BlockingQueueMessageProducer<>(blockingQueue);

	@Test
	void produce() {
		queueMessageProducer.produce(EVENT);
		assertThat(blockingQueue).containsExactly(EVENT);
	}
}
