package message_passing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessagePublishProcess<T> implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(MessagePublishProcess.class);
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
