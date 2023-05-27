package http.handler;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import io.opentelemetry.api.trace.Tracer;
import tcp.server.BufferContext;
import tcp.server.ByteBufferPool;
import tcp.server.ServerAttachmentImpl;
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
	private final Tracer tracer;
	// TODO: Remove
	private final ByteBufferPool bufferPool;

	public HTTPAcceptOperationHandler(
			Set<TokenBucket<SocketAddress>> tokenBuckets,
			int maxTokensWrite,
			int maxTokensRead,
			Function<SelectionKey, Selector> selectorSupplier,
			ByteBufferPool networkByteBufferPool,
			Tracer tracer,
			ByteBufferPool bufferPool
	) {
		this.tokenBuckets = tokenBuckets;
		this.maxTokensWrite = maxTokensWrite;
		this.maxTokensRead = maxTokensRead;
		this.selectorSupplier = selectorSupplier;
		this.networkByteBufferPool = networkByteBufferPool;
		this.tracer = tracer;
		this.bufferPool = bufferPool;
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
//			socketChannel.socket().setReceiveBufferSize(64 * 1024);
//			socketChannel.socket().setSendBufferSize(64 * 1024);
			var serverAttachment = new ServerAttachmentImpl(
					Constants.Protocol.HTTP,
					new BufferContext(networkByteBufferPool),
					new ConcurrentLinkedDeque<>(),
					new ConcurrentHashMap<>(),
					requestSpan,
					networkByteBufferPool,
					null
			);
			var newSelector = selectorSupplier.apply(selectionKey);
			final SelectionKey registered = socketChannel.register(newSelector, 0, serverAttachment);
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
