package tcp.server.handler;

import exception.ParseException;
import tcp.server.ServerAttachmentObject;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

public abstract class AbstractionMessageReaderReadOperationHandler<MessageType> implements Consumer<SelectionKey> {

	@SuppressWarnings("unchecked")
	@Override
	public void accept(SelectionKey selectionKey) {
		var socketChannel = (SocketChannel) (selectionKey.channel());
		var attachmentObject = (ServerAttachmentObject<MessageType>) (selectionKey.attachment());
		var messageReader = attachmentObject.messageReader();
		var buffer = attachmentObject.readBuffer();
		try {
			if (!buffer.hasRemaining()) {
				buffer.clear();
				socketChannel.read(buffer);
				buffer.flip();
			}
			while (buffer.hasRemaining() && !messageReader.isReady()) {
				messageReader = messageReader.read(buffer);
				if (messageReader.isReady()) {
					break;
				}
				if (!buffer.hasRemaining()) {
					buffer.clear();
					socketChannel.read(buffer);
					buffer.flip();
				}
			}
			if (messageReader.isReady()) {
				onMessageRead(messageReader.getMessage(), selectionKey);
			} else {
				selectionKey.attach(new ServerAttachmentObject<>(
								attachmentObject.protocol(),
								attachmentObject.readBuffer(),
								messageReader,
								attachmentObject.messageWriter()
				));
			}
		}
		catch (IOException | ParseException e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}

	public abstract void onMessageRead(MessageType message, SelectionKey selectionKey);

}
