package echo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

public class EchoWriteOperationHandler implements Consumer<SelectionKey> {
	@Override
	public void accept(SelectionKey selectionKey) {
		var socketChannel = (SocketChannel) (selectionKey.channel());
		var buffer = (ByteBuffer) (selectionKey.attachment());
		try {
			socketChannel.write(buffer);
			if (!buffer.hasRemaining()) {
				buffer.clear();
				selectionKey.interestOps(SelectionKey.OP_READ);
			}
		}
		catch (IOException e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}
}
