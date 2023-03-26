package tcp.server.handler;

import util.Serializable;
import request_handler.ProcessingRequest;

import java.nio.channels.SelectionKey;
import java.util.concurrent.BlockingQueue;

public abstract class GenericRequestResponseReadOperationHandler<RequestMessage, ResponseMessage extends Serializable>
				extends AbstractionMessageReaderReadOperationHandler<RequestMessage> {
	private final BlockingQueue<ProcessingRequest<RequestMessage, ResponseMessage>> requestQueue;

	public GenericRequestResponseReadOperationHandler(
		BlockingQueue<ProcessingRequest<RequestMessage, ResponseMessage>> requestQueue
	) {
		this.requestQueue = requestQueue;
	}

	@Override
	public void onMessageRead(RequestMessage message, SelectionKey selectionKey) {
		selectionKey.interestOps(SelectionKey.OP_WRITE);
		System.out.println("Successfully parsed request message - " + message);
		requestQueue.add(new ProcessingRequest<>(message) {
			@Override
			public void onResponse(ResponseMessage responseMessage) {
				onMessageResponse(responseMessage, selectionKey);
			}
		});
	}

	public abstract void onMessageResponse(ResponseMessage responseMessage, SelectionKey selectionKey);

}
