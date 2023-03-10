package echo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

public class EchoReadOperationHandler implements Consumer<SelectionKey> {
	@Override
	public void accept(SelectionKey selectionKey) {
		var socketChannel = (SocketChannel) (selectionKey.channel());
		var buffer = (ByteBuffer) (selectionKey.attachment());
		try {
			socketChannel.read(buffer);
			buffer.flip();
			selectionKey.interestOps(SelectionKey.OP_WRITE);
		}
		catch (IOException e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}
}
