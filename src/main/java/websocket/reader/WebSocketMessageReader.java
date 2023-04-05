package websocket.reader;

import exception.ParseException;
import reader.MessageReader;
import tcp.server.BufferContext;
import websocket.OpCode;
import websocket.WebSocketMessage;

import java.math.BigInteger;
import java.util.Map;

public class WebSocketMessageReader implements MessageReader<WebSocketMessage> {
	private static final Map<Integer, Integer> EXTRA_BYTES_BY_PAYLOAD_LENGTH = Map.of(126, 2, 127, 8);
	public static final int MASKING_KEY_IN_BYTES = 4;
	public static final int METADATA_IN_BYTES = 2;

	@Override
	public WebSocketMessage read(BufferContext bufferContext) throws ParseException {
		int N = bufferContext.size();
		if (N < METADATA_IN_BYTES) {
			return null;
		}
		byte firstByte = bufferContext.get(0);
		byte secondByte = bufferContext.get(1);
		var isFin = (firstByte & 0b10000000) != 0;
		var isMasked = (secondByte & 0b10000000) != 0;
		var opCode = OpCode.getByCode(firstByte & 0b00001111);
		var payloadLength = secondByte & 0b01111111;
		int expectedMinLength = METADATA_IN_BYTES;
		if (isMasked) {
			expectedMinLength += MASKING_KEY_IN_BYTES;
		}
		int extraBytes = EXTRA_BYTES_BY_PAYLOAD_LENGTH.getOrDefault(payloadLength, 0);
		if (extraBytes == 0) {
			expectedMinLength += payloadLength;
		} else {
			expectedMinLength += extraBytes;
		}
		if (N < expectedMinLength) {
			return null;
		}
		if (extraBytes != 0) {
			var extendedSize =
							new BigInteger(extractBytes(bufferContext, METADATA_IN_BYTES, METADATA_IN_BYTES + extraBytes));
			expectedMinLength += extendedSize.intValue();
			payloadLength = extendedSize.intValue();
		}
		if (N < expectedMinLength) {
			return null;
		}
		var maskingKey = isMasked
						? extractBytes(bufferContext,
						METADATA_IN_BYTES + extraBytes,
						METADATA_IN_BYTES + MASKING_KEY_IN_BYTES + extraBytes)
						: null;

		var payloadStart = (isMasked ? METADATA_IN_BYTES : 0) + MASKING_KEY_IN_BYTES + extraBytes;

		var payload = extractBytes(bufferContext, payloadStart, payloadStart + payloadLength);
		if (isMasked) {
			for (int i = 0; i < payload.length; i++) {
				payload[i] = (byte) (payload[i] ^ maskingKey[i % MASKING_KEY_IN_BYTES]);
			}
		}
		var webSocketMessage = new WebSocketMessage();
		webSocketMessage.setFin(isFin);
		webSocketMessage.setOpCode(opCode);
		webSocketMessage.setMaskingKey(maskingKey);
		webSocketMessage.setPayload(payload);
		return webSocketMessage;
	}

	private byte[] extractBytes(BufferContext bufferContext, int start, int end) {
		byte[] arr = new byte[end - start];
		for (int i = start; i < end; i++) {
			arr[i - start] = bufferContext.get(i);
		}
		return arr;
	}

}
