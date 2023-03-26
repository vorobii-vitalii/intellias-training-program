package websocket.reader;

import exception.ParseException;
import reader.MessageReader;
import websocket.WebSocketMessage;

import java.nio.ByteBuffer;

public class MaskingKeyMessageReader extends AbstractConstantBytesMessageReader<WebSocketMessage> {
	private static final int MASKING_KEY_BYTES = 4;

	private final WebSocketMessage webSocketMessage;

	public MaskingKeyMessageReader(WebSocketMessage webSocketMessage) {
		super(MASKING_KEY_BYTES);
		this.webSocketMessage = webSocketMessage;
	}

	@Override
	public MessageReader<WebSocketMessage> read(ByteBuffer buffer) throws ParseException {
		if (!webSocketMessage.isMasked()) {
			return new PayloadDataReader(webSocketMessage);
		}
		return super.read(buffer);
	}

	@Override
	public MessageReader<WebSocketMessage> onBytesRead(byte[] bytes) {
		webSocketMessage.setMaskingKey(bytes);
		return new PayloadDataReader(webSocketMessage);
	}
}
