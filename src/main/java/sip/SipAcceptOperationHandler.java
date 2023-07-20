package sip;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.function.Consumer;

import sip.request_handling.TCPConnectionsContext;

public class SipAcceptOperationHandler implements Consumer<SelectionKey> {
	private final TCPConnectionsContext tcpConnectionsContext;

	public SipAcceptOperationHandler(TCPConnectionsContext tcpConnectionsContext) {
		this.tcpConnectionsContext = tcpConnectionsContext;
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		try {
			var socketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
			if (socketChannel == null) {
				return;
			}
			tcpConnectionsContext.register(socketChannel);
		} catch (IOException e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}
}
