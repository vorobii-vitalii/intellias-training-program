package grpc;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import util.Constants;

public class TracingContextPropagator extends CallCredentials {
	private final Context context;
	private final OpenTelemetry openTelemetry;

	public TracingContextPropagator(Context context, OpenTelemetry openTelemetry) {
		this.context = context;
		this.openTelemetry = openTelemetry;
	}

	@Override
	public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
		Metadata metadata = new Metadata();
		metadata.put(Metadata.Key.of(Constants.Tracing.CONTEXT, ASCII_STRING_MARSHALLER), getSerializedContext());
		applier.apply(metadata);
	}

	@Override
	public void thisUsesUnstableApi() {
	}

	private String getSerializedContext() {
		var reference = new AtomicReference<String>();
		openTelemetry.getPropagators().getTextMapPropagator().inject(context, reference,
				(atomicReference, ignoredKey, value) -> Objects.requireNonNull(atomicReference).set(value));
		return reference.get();
	}
}
