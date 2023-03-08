package ws;

import http.HTTPRequest;
import reader.impl.HTTPRequestLineMessageReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.function.Consumer;

public class WebSocketAcceptOperationHandler implements Consumer<SelectionKey> {
	private final int bufferCapacity;

	public WebSocketAcceptOperationHandler(int bufferCapacity) {
		this.bufferCapacity = bufferCapacity;
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		try {
			var buffer = ByteBuffer.allocateDirect(bufferCapacity);
			var socketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
			var selector = selectionKey.selector();
			System.out.println("Accepted new connection " + socketChannel);
			socketChannel.configureBlocking(false);
			socketChannel.register(
							selector,
							SelectionKey.OP_READ,
							new AttachmentObject<>(Mode.HANDSHAKE, buffer, new HTTPRequestLineMessageReader(new HTTPRequest()))
			);
		} catch (IOException e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}

}
