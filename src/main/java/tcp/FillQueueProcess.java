package tcp;

import java.util.Queue;

public class FillQueueProcess<T> implements Runnable {
	private final Queue<T> queue;
	private final T msg;

	public FillQueueProcess(Queue<T> queue, T msg) {
		this.queue = queue;
		this.msg = msg;
	}

	@Override
	public void run() {
		queue.add(msg);
	}
}
