package tcp.server.handler;

import tcp.server.ServerAttachmentObject;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

public class GenericWriteOperationHandler implements Consumer<SelectionKey> {

	@Override
	public void accept(SelectionKey selectionKey) {
		var socketChannel = (SocketChannel) (selectionKey.channel());
		var attachmentObject = (ServerAttachmentObject<?>) (selectionKey.attachment());
		var messageWriter = attachmentObject.messageWriter();
		if (messageWriter == null || messageWriter.isWritten()) {
			return;
		}
		messageWriter.write(buffer -> {
				try {
					socketChannel.write(buffer);
				} catch (IOException e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
		});
	}

}
