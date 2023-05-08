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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class HTTPAcceptOperationHandler implements Consumer<SelectionKey> {
	private static final Logger LOGGER = LoggerFactory.getLogger(HTTPAcceptOperationHandler.class);
	private final Set<TokenBucket<SocketAddress>> tokenBuckets;
	private final int maxTokensWrite;
	private final int maxTokensRead;
	private final Function<SelectionKey, Selector> selectorSupplier;
	private final ByteBufferPool byteBufferPool;
	private final Tracer httpAcceptConnectionHandlerTracer;

	public HTTPAcceptOperationHandler(
			Set<TokenBucket<SocketAddress>> tokenBuckets,
			int maxTokensWrite,
			int maxTokensRead,
			Function<SelectionKey, Selector> selectorSupplier,
			ByteBufferPool byteBufferPool,
			OpenTelemetry openTelemetry
	) {
		this.tokenBuckets = tokenBuckets;
		this.maxTokensWrite = maxTokensWrite;
		this.maxTokensRead = maxTokensRead;
		this.selectorSupplier = selectorSupplier;
		this.byteBufferPool = byteBufferPool;
		httpAcceptConnectionHandlerTracer = openTelemetry.getTracer("HTTP accept connection handler");
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		try {
			var socketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
			var requestSpan = httpAcceptConnectionHandlerTracer
					.spanBuilder("New HTTP connection")
					.setAttribute("client.ip", socketChannel.getRemoteAddress().toString())
					.startSpan();
			socketChannel.socket().setSendBufferSize(2 * 1024 * 1024);
			socketChannel.socket().setReceiveBufferSize(2 * 1024 * 1024);
			socketChannel.socket().setSoTimeout(1);
			LOGGER.info("Accepted new connection {}", socketChannel);
			socketChannel.configureBlocking(false);
			TokenBucket<SocketAddress> writeTokenBucket = new TokenBucket<>(maxTokensWrite, maxTokensWrite, socketChannel.getRemoteAddress());
			TokenBucket<SocketAddress> readTokenBucket = new TokenBucket<>(maxTokensRead, maxTokensRead, socketChannel.getRemoteAddress());
			tokenBuckets.add(writeTokenBucket);
			tokenBuckets.add(readTokenBucket);
			var serverAttachment = new ServerAttachment(
					Constants.Protocol.HTTP,
					new BufferContext(byteBufferPool),
					new BufferContext(byteBufferPool),
					new ConcurrentLinkedQueue<>(),
					new ConcurrentHashMap<>(),
					writeTokenBucket,
					readTokenBucket,
					requestSpan
			);
			socketChannel.register(selectorSupplier.apply(selectionKey), SelectionKey.OP_READ, serverAttachment);
			requestSpan.end();
		} catch (IOException e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}

}
