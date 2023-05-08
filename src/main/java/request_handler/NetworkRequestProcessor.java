package request_handler;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.BlockingQueue;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

public class NetworkRequestProcessor<RequestMessage> implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(NetworkRequestProcessor.class);

	private final BlockingQueue<NetworkRequest<RequestMessage>> requestQueue;
	private final RequestHandler<RequestMessage> requestHandler;
	private final Timer requestProcessingTimer;
	private final Counter requestsCounter;

	public NetworkRequestProcessor(
			BlockingQueue<NetworkRequest<RequestMessage>> requestQueue,
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
				var request = requestQueue.take();
				requestsCounter.increment();
				requestProcessingTimer.record(() -> requestHandler.handle(request));
			}
			catch (InterruptedException e) {
				LOGGER.warn("Thread that handles requests was interrupted", e);
				Thread.currentThread().interrupt();
			}
			catch (Throwable e) {
				LOGGER.error("Exception on handle of request", e);
			}
		}
	}
}
