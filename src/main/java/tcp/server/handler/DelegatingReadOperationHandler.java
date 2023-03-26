package tcp.server.handler;

import tcp.server.ServerAttachmentObject;

import java.nio.channels.SelectionKey;
import java.util.Map;
import java.util.function.Consumer;

public class DelegatingReadOperationHandler implements Consumer<SelectionKey> {
	private final Map<String, Consumer<SelectionKey>> readHandlerByProtocolName;

	public DelegatingReadOperationHandler(Map<String, Consumer<SelectionKey>> readHandlerByProtocolName) {
		this.readHandlerByProtocolName = readHandlerByProtocolName;
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		var serverAttachmentObject = (ServerAttachmentObject<?>) selectionKey.attachment();
		readHandlerByProtocolName.get(serverAttachmentObject.protocol()).accept(selectionKey);
	}
}
