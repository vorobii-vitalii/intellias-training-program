package http.handler;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.trace.Tracer;
import tcp.server.BufferContext;
import tcp.server.ByteBufferPool;
import tcp.server.ServerAttachmentImpl;
import util.Constants;

public class HTTPAcceptOperationHandler implements Consumer<SelectionKey> {
	public static final int NOOPS = 0;
	private static final Logger LOGGER = LoggerFactory.getLogger(HTTPAcceptOperationHandler.class);
	private final Function<SelectionKey, Selector> selectorSupplier;
	private final ByteBufferPool networkByteBufferPool;
	private final Tracer tracer;

	public HTTPAcceptOperationHandler(
			Function<SelectionKey, Selector> selectorSupplier,
			ByteBufferPool networkByteBufferPool,
			Tracer tracer
	) {
		this.selectorSupplier = selectorSupplier;
		this.networkByteBufferPool = networkByteBufferPool;
		this.tracer = tracer;
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		try {
			var requestSpan = tracer
					.spanBuilder("New HTTP connection")
					.startSpan();
			var socketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
			if (socketChannel == null) {
				return;
			}
			requestSpan.addEvent("Connection accepted");
			LOGGER.debug("Accepted new connection {}", socketChannel);
			socketChannel.configureBlocking(false);
			socketChannel.socket().setSoTimeout(1);
			socketChannel.socket().setTcpNoDelay(true);
			var serverAttachment = new ServerAttachmentImpl(
					Constants.Protocol.HTTP,
					new BufferContext(networkByteBufferPool),
					new ConcurrentLinkedDeque<>(),
					new ConcurrentHashMap<>(),
					requestSpan,
					null
			);
			var newSelector = selectorSupplier.apply(selectionKey);
			var registered = socketChannel.register(newSelector, NOOPS, serverAttachment);
			serverAttachment.setSelectionKey(registered);
			registered.interestOps(SelectionKey.OP_READ);
			newSelector.wakeup();
			requestSpan.end();
		} catch (IOException e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}

}
