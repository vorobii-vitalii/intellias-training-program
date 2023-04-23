package request_handler;

public interface RequestHandler<RequestMessage> {
	void handle(NetworkRequest<RequestMessage> networkRequest);
}
