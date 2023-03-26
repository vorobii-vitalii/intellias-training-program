package websocket.handler;

import tcp.server.ServerAttachmentObject;
import tcp.server.handler.AbstractionMessageReaderReadOperationHandler;
import websocket.OpCode;
import websocket.WebSocketMessage;
import websocket.reader.InitialMetadataMessageReader;
import writer.MessageWriter;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;

public class WebSocketRequestHandler extends AbstractionMessageReaderReadOperationHandler<WebSocketMessage> {

	@SuppressWarnings("unchecked")
	@Override
	public void onMessageRead(WebSocketMessage message, SelectionKey selectionKey) {
		var attachmentObject = (ServerAttachmentObject<WebSocketMessage>) (selectionKey.attachment());
		System.out.println("Read websocket message: " + message);
		if (message.getOpCode().equals(OpCode.TEXT)) {
			System.out.println("Text = " + new String(message.getPayload()));
		}
		var messageToWrite = new WebSocketMessage();
		messageToWrite.setFin(true);
		messageToWrite.setOpCode(OpCode.TEXT);
		var replyMessage = message.getOpCode().equals(OpCode.TEXT)
						? new String(message.getPayload()) + " Reply"
						: "Message 1234567";
		messageToWrite.setPayload(replyMessage.getBytes(StandardCharsets.UTF_8));

		selectionKey.interestOps(SelectionKey.OP_WRITE);

		selectionKey.attach(new ServerAttachmentObject<>(
						attachmentObject.protocol(),
						attachmentObject.readBuffer(),
						new InitialMetadataMessageReader(new WebSocketMessage()),
						new MessageWriter(ByteBuffer.wrap(messageToWrite.serialize()), () -> {
							System.out.println("WS Message written");
							selectionKey.interestOps(SelectionKey.OP_READ);
						})
		));
	}
}
