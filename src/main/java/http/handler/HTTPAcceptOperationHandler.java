package http.handler;

import tcp.server.BufferContext;
import tcp.server.ServerAttachment;
import util.Constants;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public class HTTPAcceptOperationHandler implements Consumer<SelectionKey> {

	@Override
	public void accept(SelectionKey selectionKey) {
		try {
			var socketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
			var selector = selectionKey.selector();
			System.out.println("Accepted new connection " + socketChannel);
			socketChannel.configureBlocking(false);
			socketChannel.register(
							selector,
							SelectionKey.OP_READ,
							new ServerAttachment(
											Constants.Protocol.HTTP,
											new BufferContext(),
											new LinkedBlockingQueue<>(),
											new HashMap<>()
							));
		} catch (IOException e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}

}
