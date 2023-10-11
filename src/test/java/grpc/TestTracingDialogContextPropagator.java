package grpc;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import util.Constants;

@ExtendWith(MockitoExtension.class)
class TestTracingDialogContextPropagator {
	public static final String SERIALIZED_CONTEXT = "0A#T9se4hjy389th3";
	@Mock
	Context context;
	@Mock
	OpenTelemetry openTelemetry;
	@InjectMocks
	TracingContextPropagator tracingContextPropagator;

	@Mock
	ContextPropagators contextPropagators;

	@Mock
	TextMapPropagator textMapPropagator;

	@Mock
	CallCredentials.MetadataApplier applier;

	@Test
	void applyRequestMetadata() {
		when(openTelemetry.getPropagators()).thenReturn(contextPropagators);
		when(contextPropagators.getTextMapPropagator()).thenReturn(textMapPropagator);
		doAnswer(invocationOnMock -> {
			AtomicReference<String> carrier = invocationOnMock.getArgument(1);
			TextMapSetter<AtomicReference<String>> textMapSetter = invocationOnMock.getArgument(2);
			textMapSetter.set(carrier, "IgnoredKey", SERIALIZED_CONTEXT);
			return null;
		}).when(textMapPropagator).inject(eq(context), any(AtomicReference.class), any());
		tracingContextPropagator.applyRequestMetadata(null, null, applier);
		verify(applier).apply(argThat(
				metadata -> SERIALIZED_CONTEXT.equals(metadata.get(Metadata.Key.of(Constants.Tracing.CONTEXT, ASCII_STRING_MARSHALLER)))));
	}

}
