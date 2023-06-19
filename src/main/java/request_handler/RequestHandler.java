package request_handler;

public interface RequestHandler<T> {
	void handle(T request);
}
