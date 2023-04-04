package tcp.server.handler;

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
	private final Map<String, BiConsumer<SelectionKey, Serializable>> onWriteHandlerByProtocolName;

	public WriteOperationHandler(Map<String, BiConsumer<SelectionKey, Serializable>> onWriteHandlerByProtocolName) {
		this.onWriteHandlerByProtocolName = onWriteHandlerByProtocolName;
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		var socketChannel = (SocketChannel) (selectionKey.channel());
		var attachmentObject = (ServerAttachment) (selectionKey.attachment());
		var responses = attachmentObject.responses();
		if (responses == null) {
			return;
		}
		while (selectionKey.isWritable() && !responses.isEmpty()) {
			var response = responses.poll();
			var buffer = ByteBuffer.wrap(response.serialize());
			while (buffer.hasRemaining()) {
				try {
					socketChannel.write(buffer);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
			var handler = onWriteHandlerByProtocolName.get(attachmentObject.protocol());
			if (handler != null) {
				handler.accept(selectionKey, response);
			}
		}
	}

}
