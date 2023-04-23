package request_handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.BlockingQueue;

public class NetworkRequestProcessor<RequestMessage> implements Runnable {
	private static final Logger LOGGER = LogManager.getLogger(NetworkRequestProcessor.class);

	private final BlockingQueue<NetworkRequest<RequestMessage>> requestQueue;
	private final RequestHandler<RequestMessage> requestHandler;

	public NetworkRequestProcessor(
		BlockingQueue<NetworkRequest<RequestMessage>> requestQueue,
		RequestHandler<RequestMessage> requestHandler
	) {
		this.requestQueue = requestQueue;
		this.requestHandler = requestHandler;
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			try {
				var request = requestQueue.take();
				requestHandler.handle(request);
			} catch (InterruptedException e) {
				LOGGER.warn("Thread that handles requests was interrupted", e);
				Thread.currentThread().interrupt();
			}
			catch (Exception e) {
				LOGGER.error("Exception on handle of request", e);
			}
		}
	}
}
