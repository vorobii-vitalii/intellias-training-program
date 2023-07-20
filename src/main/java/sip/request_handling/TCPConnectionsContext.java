package sip.request_handling;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.jcip.annotations.ThreadSafe;
import sip.Address;

@ThreadSafe
public class TCPConnectionsContext {
	// TODO: Periodical removal
	private final Map<Address, SelectionKey> socketConnectionsMap = new ConcurrentHashMap<>();
	private final ConnectionPreparer connectionPreparer;

	public TCPConnectionsContext(ConnectionPreparer connectionPreparer) {
		this.connectionPreparer = connectionPreparer;
	}

	public void register(SocketChannel socketChannel) {
		try {
			var selectionKey = connectionPreparer.prepareConnection(socketChannel);
			var remoteAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
			socketConnectionsMap.put(new Address(remoteAddress.getHostName(), remoteAddress.getPort()), selectionKey);
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to prepare connection", e);
		}
	}

	public SelectionKey establishConnection(Address address) {
		return socketConnectionsMap.compute(address, (ignored, selectionKey) -> {
			if (selectionKey != null) {
				return selectionKey;
			}
			try {
				var createdSocketChannel = SocketChannel.open(new InetSocketAddress(address.host(), address.port()));
				return connectionPreparer.prepareConnection(createdSocketChannel);
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

}
