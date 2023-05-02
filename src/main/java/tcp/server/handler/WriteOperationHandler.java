package tcp.server.handler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import tcp.server.ServerAttachment;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class WriteOperationHandler implements Consumer<SelectionKey> {
	public static final int NO_BYTES_WRITTEN = 0;
	private final Timer timer;
	private final Counter messagesWrittenCounter;
	private final BiConsumer<SelectionKey, Throwable> onError;

	public WriteOperationHandler(Timer timer, Counter messagesWrittenCounter, BiConsumer<SelectionKey, Throwable> onError) {
		this.timer = timer;
		this.messagesWrittenCounter = messagesWrittenCounter;
		this.onError = onError;
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		timer.record(() -> {
			var socketChannel = (WritableByteChannel) (selectionKey.channel());
			var attachmentObject = (ServerAttachment) (selectionKey.attachment());
			var responses = attachmentObject.responses();
			if (responses == null) {
				return;
			}
			try {
				while (!responses.isEmpty()) {
					var response = responses.poll();
					boolean canAcceptMore = true;
					while (response.hasRemaining()) {
						if (socketChannel.write(response) == NO_BYTES_WRITTEN) {
							canAcceptMore = false;
							break;
						}
					}
					if (!canAcceptMore) {
						break;
					}
					responses.poll();
					messagesWrittenCounter.increment();
				}
				if (responses.isEmpty()) {
					selectionKey.interestOps(SelectionKey.OP_READ);
				}
			} catch (Throwable e) {
				onError.accept(selectionKey, e);
			}
		});
	}

}
