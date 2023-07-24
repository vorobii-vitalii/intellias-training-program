package udp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jcip.annotations.NotThreadSafe;
import sip.Address;
import sip.request_handling.media.MediaMappingStorage;
import tcp.server.BufferCopier;

@NotThreadSafe
@SuppressWarnings("raw")
public class ForwardingUDPReadOperationHandler implements Consumer<SelectionKey> {
	private static final Logger LOGGER = LoggerFactory.getLogger(ForwardingUDPReadOperationHandler.class);

	private final ByteBuffer byteBuffer;
	private final BufferCopier bufferCopier;
	private final MediaMappingStorage mediaMappingStorage;
	private final Collection<PacketTypeRecognizer> packetTypeRecognizers;
	private final Map<Address, Deque<ByteBuffer>> map;

	public ForwardingUDPReadOperationHandler(
			int maxMessageSize,
			MediaMappingStorage mediaMappingStorage,
			BufferCopier bufferCopier,
			Collection<PacketTypeRecognizer> packetTypeRecognizers,
			// TODO: Create smth more generic
			Map<Address, Deque<ByteBuffer>> map
	) {
		this.byteBuffer = ByteBuffer.allocate(maxMessageSize);
		this.bufferCopier = bufferCopier;
		this.mediaMappingStorage = mediaMappingStorage;
		this.packetTypeRecognizers = packetTypeRecognizers;
		this.map = map;
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		var channel = (DatagramChannel) selectionKey.channel();
		try {
			var socketAddress = channel.receive(byteBuffer);
			// Message received
			if (socketAddress != null) {
				var bytesSource = new ByteBufferSource(byteBuffer);
				var packetType = packetTypeRecognizers.stream()
						.map(packetTypeRecognizer -> packetTypeRecognizer.getType(bytesSource))
						.filter(Objects::nonNull)
						.findFirst()
						.orElseGet(() -> {
							LOGGER.warn("Received packet from {} of unknown type...", socketAddress);
							return null;
						});
				LOGGER.info("Packet from {} has type = {}", socketAddress, packetType);
				var address = Address.fromSocketAddress(socketAddress);
				var receivers = mediaMappingStorage.getReceivers(address, packetType);
				for (var receiver : receivers) {
					map.compute(receiver, (receivedAddr, byteBuffers) -> {
						if (byteBuffers == null) {
							byteBuffers = new LinkedList<>();
						}
						byteBuffers.addLast(bufferCopier.copy(byteBuffer));
						return byteBuffers;
					});
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
