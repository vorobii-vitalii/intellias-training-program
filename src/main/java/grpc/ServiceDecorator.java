package grpc;

import io.grpc.stub.AbstractStub;

public interface ServiceDecorator {
	<R extends AbstractStub<R>, T extends AbstractStub<R>> R decorateService(T service);
}
