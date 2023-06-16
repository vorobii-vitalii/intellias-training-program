package grpc;

import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;

public abstract class FlowControlClientResponseObserver<A, B> implements ClientResponseObserver<A, B> {

	private ClientCallStreamObserver<A> requestStream;
	private final A request;

	protected FlowControlClientResponseObserver(A request) {
		this.request = request;
	}

	@Override
	public void beforeStart(ClientCallStreamObserver<A> requestStream) {
		this.requestStream = requestStream;
		requestStream.disableAutoInboundFlowControl();
		requestStream.setOnReadyHandler(requestStream::onCompleted);
	}

	public void requestAnotherBatch() {
		requestStream.request(1);
	}

}
