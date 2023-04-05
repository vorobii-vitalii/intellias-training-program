package request_handler;

import util.Serializable;

public interface RequestHandler<RequestMessage, ResponseMessage extends Serializable> {
	void handle(ProcessingRequest<RequestMessage, ResponseMessage> processingRequest);
}