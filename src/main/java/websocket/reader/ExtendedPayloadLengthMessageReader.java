package websocket.reader;

import reader.MessageReader;
import websocket.WebSocketMessage;

import java.math.BigInteger;
import java.util.Arrays;

public class ExtendedPayloadLengthMessageReader extends AbstractConstantBytesMessageReader<WebSocketMessage> {
	private final WebSocketMessage webSocketMessage;

	public ExtendedPayloadLengthMessageReader(
					WebSocketMessage webSocketMessage,
					int extendedPayloadLengthInBytes
	) {
		super(extendedPayloadLengthInBytes);
		this.webSocketMessage = webSocketMessage;
	}

	@Override
	public MessageReader<WebSocketMessage> onBytesRead(byte[] bytes) {
		System.out.println("Before " + new BigInteger(bytes) + " " + Arrays.toString(bytes));
		webSocketMessage.setPayloadLength(new BigInteger(bytes).intValue());
		return new MaskingKeyMessageReader(webSocketMessage);
	}
}
