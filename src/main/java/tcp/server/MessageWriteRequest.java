package tcp.server;

import java.nio.ByteBuffer;

import io.opentelemetry.api.trace.Span;

public record MessageWriteRequest(ByteBuffer message, Span parentSpan) {
}
