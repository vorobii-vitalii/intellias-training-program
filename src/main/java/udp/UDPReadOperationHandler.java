package udp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import message_passing.MessageProducer;
import net.jcip.annotations.NotThreadSafe;
import tcp.server.reader.MessageReader;
import util.Pair;

@NotThreadSafe
@SuppressWarnings("raw")
public class UDPReadOperationHandler implements Consumer<SelectionKey> {
	private static final Logger LOGGER = LoggerFactory.getLogger(UDPReadOperationHandler.class);

	private final ByteBuffer byteBuffer;
	private final List<Pair<MessageReader, MessageProducer>> pairs;

	public UDPReadOperationHandler(
			int maxMessageSize,
			List<Pair<MessageReader, MessageProducer>> pairs
	) {
		this.pairs = pairs;
		this.byteBuffer = ByteBuffer.allocate(maxMessageSize);
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		var channel = (DatagramChannel) selectionKey.channel();
		try {
			var socketAddress = channel.receive(byteBuffer);
			// Message received
			if (socketAddress != null) {
				var bytesSource = new ByteBufferSource(byteBuffer);
				for (var pair : pairs) {
					var readRes = pair.first().read(bytesSource, e -> {
					});
					if (readRes != null) {
						pair.second().produce(new UDPPacket<>(readRes.first(), socketAddress));
					}
				}
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
