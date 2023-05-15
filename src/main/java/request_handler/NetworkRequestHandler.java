package request_handler;

public interface NetworkRequestHandler<RequestMessage> {
	void handle(NetworkRequest<RequestMessage> networkRequest);
}
