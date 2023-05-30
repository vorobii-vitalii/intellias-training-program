package tcp;

import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FillQueueProcess<T> implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(FillQueueProcess.class);
	private final Queue<T> queue;
	private final T msg;

	public FillQueueProcess(Queue<T> queue, T msg) {
		this.queue = queue;
		this.msg = msg;
	}

	@Override
	public void run() {
		LOGGER.info("Writing message {}", msg);
		queue.add(msg);
	}
}
