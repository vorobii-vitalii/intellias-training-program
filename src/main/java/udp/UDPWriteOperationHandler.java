package udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.Deque;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sip.Address;

public class UDPWriteOperationHandler implements Consumer<SelectionKey> {
	private static final Logger LOGGER = LoggerFactory.getLogger(UDPWriteOperationHandler.class);

	// TODO: What if client disconnects? -> instead of List<Buffer> - PriorityQueue<Pair<Buffer, Instant>> cfunc = Instant
	private final Map<Address, Deque<ByteBuffer>> map;

	public UDPWriteOperationHandler(Map<Address, Deque<ByteBuffer>> map) {
		this.map = map;
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		var channel = (DatagramChannel) selectionKey.channel();
		try {
			for (var entry : map.entrySet()) {
				var address = entry.getKey();
				var socketAddress = new InetSocketAddress(address.host(), address.port());
				var buffers = entry.getValue();
				while (!buffers.isEmpty()) {
					var buffer = buffers.peekFirst();
					channel.send(buffer, socketAddress);
					if (buffer.hasRemaining()) {
						// Client cannot accept more...
						break;
					}
					LOGGER.info("Written message to {}", socketAddress);
					buffers.pollFirst();
				}
			}
		}
		catch (IOException exception) {
			LOGGER.warn("UDP writer error", exception);
		}
		if (map.isEmpty()) {
			// Otherwise waste of cycles...
			selectionKey.interestOps(SelectionKey.OP_READ);
		}
	}
}
