package request_handler;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.Queue;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

public class RequestProcessor<RequestMessage> implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(RequestProcessor.class);

	private final Queue<RequestMessage> requestQueue;
	private final RequestHandler<RequestMessage> requestHandler;
	private final Timer requestProcessingTimer;
	private final Counter requestsCounter;

	public RequestProcessor(
			Queue<RequestMessage> requestQueue,
			RequestHandler<RequestMessage> requestHandler,
			Timer requestProcessingTimer,
			Counter requestsCounter
	) {
		this.requestQueue = requestQueue;
		this.requestHandler = requestHandler;
		this.requestProcessingTimer = requestProcessingTimer;
		this.requestsCounter = requestsCounter;
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			try {
				var request = requestQueue.poll();
				if (request != null) {
					if (LOGGER.isDebugEnabled()) {
						LOGGER.info("Handling {}", request);
					}
					requestsCounter.increment();
					requestProcessingTimer.record(() -> requestHandler.handle(request));
				}
			} catch (Throwable e) {
				LOGGER.error("Exception on handle of request", e);
			}
		}
	}
}
