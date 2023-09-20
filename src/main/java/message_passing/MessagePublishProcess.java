package message_passing;

public class MessagePublishProcess<T> implements Runnable {
	private final MessageProducer<T> queue;
	private final T msg;

	public MessagePublishProcess(MessageProducer<T> queue, T msg) {
		this.queue = queue;
		this.msg = msg;
	}

	@Override
	public void run() {
		queue.produce(msg);
	}
}
