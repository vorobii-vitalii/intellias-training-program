package sip;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tcp.server.BufferContext;
import tcp.server.ByteBufferPool;
import tcp.server.ServerAttachmentImpl;
import util.Constants;

public class SipAcceptOperationHandler implements Consumer<SelectionKey> {
	private static final Logger LOGGER = LoggerFactory.getLogger(SipAcceptOperationHandler.class);
	private final ByteBufferPool networkByteBufferPool;

	public SipAcceptOperationHandler(ByteBufferPool networkByteBufferPool) {
		this.networkByteBufferPool = networkByteBufferPool;
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		try {
			var socketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
			if (socketChannel == null) {
				return;
			}
			LOGGER.debug("Accepted new connection {}", socketChannel);
			socketChannel.configureBlocking(false);
			var serverAttachment = new ServerAttachmentImpl(
					Constants.Protocol.SIP,
					new BufferContext(networkByteBufferPool),
					new ConcurrentLinkedDeque<>(),
					new ConcurrentHashMap<>(),
					null,
					null
			);
			var newSelectionKey = socketChannel.register(selectionKey.selector(), SelectionKey.OP_READ, serverAttachment);
			serverAttachment.setSelectionKey(newSelectionKey);
		} catch (IOException e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}
}
