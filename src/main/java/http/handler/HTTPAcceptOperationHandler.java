package http.handler;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import tcp.server.BufferContext;
import tcp.server.ByteBufferPool;
import tcp.server.ServerAttachment;
import token_bucket.TokenBucket;
import util.Constants;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import java.util.function.Function;

public class HTTPAcceptOperationHandler implements Consumer<SelectionKey> {
	private static final Logger LOGGER = LoggerFactory.getLogger(HTTPAcceptOperationHandler.class);
	private final Set<TokenBucket<SocketAddress>> tokenBuckets;
	private final int maxTokensWrite;
	private final int maxTokensRead;
	private final Function<SelectionKey, Selector> selectorSupplier;
	private final ByteBufferPool networkByteBufferPool;
	private final Tracer httpAcceptConnectionHandlerTracer;
	private final ByteBufferPool bufferPool;

	public HTTPAcceptOperationHandler(
			Set<TokenBucket<SocketAddress>> tokenBuckets,
			int maxTokensWrite,
			int maxTokensRead,
			Function<SelectionKey, Selector> selectorSupplier,
			ByteBufferPool networkByteBufferPool,
			OpenTelemetry openTelemetry,
			ByteBufferPool bufferPool
	) {
		this.tokenBuckets = tokenBuckets;
		this.maxTokensWrite = maxTokensWrite;
		this.maxTokensRead = maxTokensRead;
		this.selectorSupplier = selectorSupplier;
		this.networkByteBufferPool = networkByteBufferPool;
		httpAcceptConnectionHandlerTracer = openTelemetry.getTracer("HTTP accept connection handler");
		this.bufferPool = bufferPool;
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		try {
			var requestSpan = httpAcceptConnectionHandlerTracer
					.spanBuilder("New HTTP connection")
					.startSpan();
			var socketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
			if (socketChannel == null) {
				return;
			}
			requestSpan.addEvent("Connection accepted");
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Accepted new connection {}", socketChannel);
			}
			socketChannel.configureBlocking(false);
			//				TokenBucket<SocketAddress> writeTokenBucket = new TokenBucket<>(maxTokensWrite, maxTokensWrite, socketChannel.getRemoteAddress());
			//				TokenBucket<SocketAddress> readTokenBucket = new TokenBucket<>(maxTokensRead, maxTokensRead, socketChannel.getRemoteAddress());
			//				tokenBuckets.add(writeTokenBucket);
			//				tokenBuckets.add(readTokenBucket);
			// executor
			var serverAttachment = new ServerAttachment(
					Constants.Protocol.HTTP,
					new BufferContext(networkByteBufferPool),
					new BufferContext(bufferPool),
					new ConcurrentLinkedDeque<>(),
					new ConcurrentHashMap<>(),
					null,
					null,
					requestSpan,
					networkByteBufferPool,
					null
			);
			var newSelector = selectorSupplier.apply(selectionKey);
			serverAttachment.setSelectionKey(socketChannel.register(newSelector, SelectionKey.OP_READ, serverAttachment));
			newSelector.wakeup();
			requestSpan.end();
		} catch (IOException e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}

}
