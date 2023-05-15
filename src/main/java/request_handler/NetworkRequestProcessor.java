package request_handler;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.Queue;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

public class NetworkRequestProcessor<RequestMessage> implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(NetworkRequestProcessor.class);

	private final Queue<NetworkRequest<RequestMessage>> requestQueue;
	private final NetworkRequestHandler<RequestMessage> networkRequestHandler;
	private final Timer requestProcessingTimer;
	private final Counter requestsCounter;

	public NetworkRequestProcessor(
			Queue<NetworkRequest<RequestMessage>> requestQueue,
			NetworkRequestHandler<RequestMessage> networkRequestHandler,
			Timer requestProcessingTimer,
			Counter requestsCounter
	) {
		this.requestQueue = requestQueue;
		this.networkRequestHandler = networkRequestHandler;
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
						LOGGER.debug("Handling {}", request);
					}
					requestsCounter.increment();
					requestProcessingTimer.record(() -> networkRequestHandler.handle(request));
				}
			} catch (Throwable e) {
				LOGGER.error("Exception on handle of request", e);
			}
		}
	}
}
