package tcp.server;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.function.Consumer;

import io.opentelemetry.api.trace.Span;
import util.UnsafeConsumer;

public record MessageWriteRequest(
		ByteBuffer message,
		UnsafeConsumer<SelectionKey> onWriteResponseCallback
) {
}
