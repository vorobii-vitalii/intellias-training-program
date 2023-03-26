package websocket.handler;

import http.handler.ProtocolChanger;
import tcp.server.ServerAttachmentObject;
import util.Constants;
import websocket.WebSocketMessage;
import websocket.reader.InitialMetadataMessageReader;

import java.nio.channels.SelectionKey;

public class WebSocketProtocolChanger implements ProtocolChanger {
	@Override
	public void changeProtocol(SelectionKey selectionKey) {
		var attachmentObject = (ServerAttachmentObject<?>) (selectionKey.attachment());
		attachmentObject.readBuffer().position(attachmentObject.readBuffer().limit());
		selectionKey.attach(new ServerAttachmentObject<>(
						Constants.Protocol.WEB_SOCKET,
						attachmentObject.readBuffer(),
						new InitialMetadataMessageReader(new WebSocketMessage()),
						null
		));
		selectionKey.interestOps(SelectionKey.OP_READ);
	}

	@Override
	public String getProtocolName() {
		return "websocket";
	}
}
