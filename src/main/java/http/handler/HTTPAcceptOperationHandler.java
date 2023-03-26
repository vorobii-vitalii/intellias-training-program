package http.handler;

import http.HTTPRequest;
import http.reader.HTTPRequestLineMessageReader;
import tcp.server.ServerAttachmentObject;
import util.Constants;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.function.Consumer;

public class HTTPAcceptOperationHandler implements Consumer<SelectionKey> {
	private final int bufferCapacity;

	public HTTPAcceptOperationHandler(int bufferCapacity) {
		this.bufferCapacity = bufferCapacity;
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		try {
			var buffer = ByteBuffer.allocateDirect(bufferCapacity);
			buffer.position(buffer.limit());
			var socketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
			var selector = selectionKey.selector();
			System.out.println("Accepted new connection " + socketChannel);
			socketChannel.configureBlocking(false);
			socketChannel.register(
							selector,
							SelectionKey.OP_READ,
							new ServerAttachmentObject<>(
											Constants.Protocol.HTTP,
											buffer,
											new HTTPRequestLineMessageReader(new HTTPRequest()),
											null
							)
			);
		} catch (IOException e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}

}
