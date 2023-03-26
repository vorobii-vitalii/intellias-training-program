package request_handler;

import util.Serializable;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

public class RequestProcessor<RequestMessage, ResponseMessage extends Serializable> implements Runnable {
	private final BlockingQueue<ProcessingRequest<RequestMessage, ResponseMessage>> requestQueue;
	private final ExecutorService executorService;
	private final RequestHandler<RequestMessage, ResponseMessage> requestHandler;

	public RequestProcessor(
		BlockingQueue<ProcessingRequest<RequestMessage, ResponseMessage>> requestQueue,
		ExecutorService executorService,
		RequestHandler<RequestMessage, ResponseMessage> requestHandler
	) {
		this.requestQueue = requestQueue;
		this.executorService = executorService;
		this.requestHandler = requestHandler;
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			try {
				var request = requestQueue.take();
				executorService.submit(() -> requestHandler.handle(request));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}
}
