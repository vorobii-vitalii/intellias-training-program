package request_handler;

import io.opentelemetry.api.trace.Span;
import tcp.server.SocketConnection;

public record NetworkRequest<Request>(Request request, SocketConnection socketConnection, Span span) {
}
