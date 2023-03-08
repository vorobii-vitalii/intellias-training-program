package ws;

import exception.ParseException;
import util.Serializable;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

public abstract class GenericRequestResponseReadOperationHandler<RequestMessage, ResponseMessage extends Serializable>
				implements Consumer<SelectionKey> {
	private final BlockingQueue<ProcessingRequest<RequestMessage, ResponseMessage>> requestQueue;

	public GenericRequestResponseReadOperationHandler(
		BlockingQueue<ProcessingRequest<RequestMessage, ResponseMessage>> requestQueue
	) {
		this.requestQueue = requestQueue;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void accept(SelectionKey selectionKey) {
		var socketChannel = (SocketChannel) (selectionKey.channel());
		var attachmentObject = (AttachmentObject<RequestMessage>) (selectionKey.attachment());
		var messageReader = attachmentObject.messageReader();
		var buffer = attachmentObject.buffer();
		try {
			if (!buffer.hasRemaining()) {
				socketChannel.read(buffer);
				buffer.flip();
			}
			while (buffer.hasRemaining() && !messageReader.isReady()) {
				messageReader = messageReader.read(buffer);
				if (!buffer.hasRemaining()) {
					buffer.clear();
					socketChannel.read(buffer);
					buffer.flip();
				}
			}
			if (messageReader.isReady()) {
				selectionKey.interestOps(0);
				var requestMessage = messageReader.getMessage();
				requestQueue.add(new ProcessingRequest<>(requestMessage) {
					@Override
					public void onResponse(ResponseMessage responseMessage) {
						onMessageResponse(responseMessage, selectionKey);
					}
				});
			}
		}
		catch (IOException | ParseException e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}

	public abstract void onMessageResponse(ResponseMessage responseMessage, SelectionKey selectionKey);

}
