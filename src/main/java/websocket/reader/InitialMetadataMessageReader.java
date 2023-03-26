package websocket.reader;

import reader.MessageReader;
import websocket.OpCode;
import websocket.WebSocketMessage;

public class InitialMetadataMessageReader extends AbstractConstantBytesMessageReader<WebSocketMessage> {
	private static final int METADATA_NUM_BYTES = 2;
	private final WebSocketMessage webSocketMessage;

	public InitialMetadataMessageReader(WebSocketMessage webSocketMessage) {
		super(METADATA_NUM_BYTES);
		this.webSocketMessage = webSocketMessage;
	}

	@Override
	public MessageReader<WebSocketMessage> onBytesRead(byte[] bytes) {
		byte firstByte = bytes[0];
		byte secondByte = bytes[1];
		var isFin = (firstByte & 0b10000000) != 0;
		var shouldMask = (secondByte & 0b10000000) != 0;
		var opCode = OpCode.getByCode(firstByte & 0b00001111);
		var payloadLength = secondByte & 0b01111111;
		webSocketMessage.setFin(isFin);
		webSocketMessage.setOpCode(opCode);
		webSocketMessage.setMasked(shouldMask);
		System.out.println(webSocketMessage + " length = " + payloadLength);
		if (payloadLength == 126) {
			return new ExtendedPayloadLengthMessageReader(webSocketMessage, 2);
		}
		else if (payloadLength == 127) {
			return new ExtendedPayloadLengthMessageReader(webSocketMessage, 8);
		}
		webSocketMessage.setPayloadLength(payloadLength);
		return new MaskingKeyMessageReader(webSocketMessage);
	}

}
