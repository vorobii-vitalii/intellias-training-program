package message_passing;

import java.util.Queue;

public class QueueMessageProducer<E> implements MessageProducer<E> {
	private final Queue<E> queue;

	public QueueMessageProducer(Queue<E> queue) {
		this.queue = queue;
	}

	@Override
	public void produce(E event) {
		queue.add(event);
	}
}
