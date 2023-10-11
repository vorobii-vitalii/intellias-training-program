package grpc;

import io.grpc.stub.AbstractStub;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;

public class ContextPropagationServiceDecorator implements ServiceDecorator {
	private final OpenTelemetry openTelemetry;

	public ContextPropagationServiceDecorator(OpenTelemetry openTelemetry) {
		this.openTelemetry = openTelemetry;
	}

	@Override
	public <R extends AbstractStub<R>, T extends AbstractStub<R>> R decorateService(T service) {
		return service.withCallCredentials(new TracingContextPropagator(Context.current(), openTelemetry));
	}
}
