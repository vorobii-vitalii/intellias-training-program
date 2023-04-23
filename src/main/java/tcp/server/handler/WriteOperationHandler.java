package tcp.server.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tcp.server.ServerAttachment;
import util.Serializable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class WriteOperationHandler implements Consumer<SelectionKey> {
	private static final Logger LOGGER = LogManager.getLogger(WriteOperationHandler.class);

	@Override
	public void accept(SelectionKey selectionKey) {
		var socketChannel = (SocketChannel) (selectionKey.channel());
		var attachmentObject = (ServerAttachment) (selectionKey.attachment());
		var responses = attachmentObject.responses();
		if (responses == null) {
			return;
		}
		try {
			while (selectionKey.isWritable() && !responses.isEmpty()) {
				var response = responses.poll();
				var buffer = ByteBuffer.wrap(response.serialize());
				while (buffer.hasRemaining()) {
					socketChannel.write(buffer);
				}
			}
			selectionKey.interestOps(SelectionKey.OP_READ);
		}
		catch (IOException e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}

}
