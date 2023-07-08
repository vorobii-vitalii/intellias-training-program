package udp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import message_passing.MessageProducer;
import net.jcip.annotations.NotThreadSafe;
import tcp.server.reader.MessageReader;

@NotThreadSafe
public class UDPReadOperationHandler<T> implements Consumer<SelectionKey> {
	private static final Logger LOGGER = LoggerFactory.getLogger(UDPReadOperationHandler.class);

	private final MessageProducer<UDPPacket<T>> messageProducer;
	private final MessageReader<T> messageReader;
	private final ByteBuffer byteBuffer;

	public UDPReadOperationHandler(
			int maxMessageSize,
			MessageProducer<UDPPacket<T>> messageProducer,
			MessageReader<T> messageReader
	) {
		this.messageProducer = messageProducer;
		this.messageReader = messageReader;
		this.byteBuffer = ByteBuffer.allocate(maxMessageSize);
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		var channel = (DatagramChannel) selectionKey.channel();
		try {
			var socketAddress = channel.receive(byteBuffer);
			// Message received
			if (socketAddress != null) {
				var pair = messageReader.read(new ByteBufferSource(byteBuffer), e -> {
				});
				if (pair == null) {
					throw new IllegalStateException("Message wasn't read!");
				}
				messageProducer.produce(new UDPPacket<>(pair.first(), socketAddress));
			}
		}
		catch (IOException error) {
			LOGGER.warn("Error when receiving UDP datagram", error);
		}
		finally {
			byteBuffer.clear();
		}
	}
}
