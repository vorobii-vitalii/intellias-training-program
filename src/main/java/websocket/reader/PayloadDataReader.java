package websocket.reader;

import reader.MessageReader;
import reader.CompletedMessageReader;
import websocket.WebSocketMessage;

public class PayloadDataReader extends AbstractConstantBytesMessageReader<WebSocketMessage> {
	private final WebSocketMessage webSocketMessage;

	public PayloadDataReader(WebSocketMessage webSocketMessage) {
		super(webSocketMessage.getPayloadLength());
		this.webSocketMessage = webSocketMessage;
	}

	@Override
	public MessageReader<WebSocketMessage> onBytesRead(byte[] bytes) {
		if (webSocketMessage.isMasked()) {
			var maskingKey = webSocketMessage.getMaskingKey();
			for (var i = 0; i < bytes.length; i++) {
				bytes[i] = (byte) (bytes[i] ^ maskingKey[i % maskingKey.length]);
			}
		}
		webSocketMessage.setPayload(bytes);
		return new CompletedMessageReader<>(webSocketMessage);
	}
}
