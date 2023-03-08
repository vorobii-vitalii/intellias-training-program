package echo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.function.Consumer;

public class EchoAcceptOperationHandler implements Consumer<SelectionKey> {
	private final int bufferCapacity;

	public EchoAcceptOperationHandler(int bufferCapacity) {
		if (bufferCapacity <= 0) {
			throw new IllegalArgumentException("buffer capacity <= 0");
		}
		this.bufferCapacity = bufferCapacity;
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		var buffer = ByteBuffer.allocateDirect(bufferCapacity);
		try {
			var socketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
			var selector = selectionKey.selector();
			System.out.println("Accepted new connection " + socketChannel);
			socketChannel.configureBlocking(false);
			socketChannel.register(selector, SelectionKey.OP_READ, buffer);
		} catch (IOException e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}
}
