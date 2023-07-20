package sip.request_handling;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tcp.server.BufferContext;
import tcp.server.ByteBufferPool;
import tcp.server.ServerAttachmentImpl;
import util.Constants;

public class SIPConnectionPreparer implements ConnectionPreparer {
	private static final Logger LOGGER = LoggerFactory.getLogger(SIPConnectionPreparer.class);

	private final ByteBufferPool networkByteBufferPool;
	private final Selector selector;

	public SIPConnectionPreparer(ByteBufferPool networkByteBufferPool, Selector selector) {
		this.networkByteBufferPool = networkByteBufferPool;
		this.selector = selector;
	}

	@Override
	public SelectionKey prepareConnection(SocketChannel socketChannel) throws IOException {
		LOGGER.debug("Preparing new connection {}", socketChannel);
		socketChannel.configureBlocking(false);
		var serverAttachment = new ServerAttachmentImpl(
				Constants.Protocol.SIP,
				new BufferContext(networkByteBufferPool),
				new ConcurrentLinkedDeque<>(),
				new ConcurrentHashMap<>(),
				null,
				null
		);
		var newSelectionKey = socketChannel.register(selector, SelectionKey.OP_READ, serverAttachment);
		serverAttachment.setSelectionKey(newSelectionKey);
		return newSelectionKey;
	}
}
