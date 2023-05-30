package request_handler;

import java.util.Queue;

public class DelegatingNetworkRequestHandler<RequestMessage> implements NetworkRequestHandler<RequestMessage> {
	private final Queue<NetworkRequest<RequestMessage>>[] queues;

	public DelegatingNetworkRequestHandler(Queue<NetworkRequest<RequestMessage>>... queues) {
		this.queues = queues;
	}

	@Override
	public void handle(NetworkRequest<RequestMessage> networkRequest) {
		var hashCode = networkRequest.socketConnection().hashCode() & 0x7fffffff;
		queues[hashCode % queues.length].add(networkRequest);
	}
}
