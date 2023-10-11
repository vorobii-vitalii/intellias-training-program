package message_passing;

import java.util.concurrent.BlockingQueue;

public class BlockingQueueMessageProducer<E> implements MessageProducer<E> {
	private final BlockingQueue<E> blockingQueue;

	public BlockingQueueMessageProducer(BlockingQueue<E> blockingQueue) {
		this.blockingQueue = blockingQueue;
	}

	@Override
	public void produce(E event) {
		try {
			blockingQueue.put(event);
		}
		catch (InterruptedException e) {
			throw new IllegalStateException("Thread was interrupted", e);
		}
	}
}
