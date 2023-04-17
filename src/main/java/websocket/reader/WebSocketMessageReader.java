package websocket.reader;

import tcp.server.reader.exception.ParseException;
import tcp.server.reader.MessageReader;
import tcp.server.BufferContext;
import util.Pair;
import util.ByteUtils;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

import java.math.BigInteger;
import java.util.Map;

public class WebSocketMessageReader implements MessageReader<WebSocketMessage> {
	private static final Map<Integer, Integer> EXTRA_BYTES_BY_PAYLOAD_LENGTH = Map.of(126, 2, 127, 8);
	public static final int MASKING_KEY_IN_BYTES = 4;
	private static final int METADATA_IN_BYTES = 2;
	private static final int IS_FIN_BITMASK = 0b10000000;
	private static final int IS_MASKED_BITMASK = 0b10000000;
	private static final int OP_CODE_BITMASK = 0b00001111;
	private static final int PAYLOAD_LENGTH_BITMASK = 0b01111111;

	@Override
		public Pair<WebSocketMessage, Integer> read(BufferContext bufferContext) throws ParseException {
			int N = bufferContext.size();
			if (N < METADATA_IN_BYTES) {
				return null;
			}
			var isFin = isFin(bufferContext);
			var isMasked = isMasked(bufferContext);
			var opCode = getOpCode(bufferContext);
			var payloadLength = getPayloadLength(bufferContext);
			var expectedMinLength = METADATA_IN_BYTES + (isMasked ? MASKING_KEY_IN_BYTES : 0);
			var extraPayloadLengthBytes = EXTRA_BYTES_BY_PAYLOAD_LENGTH.getOrDefault(payloadLength, 0);
			if (extraPayloadLengthBytes == 0) {
				expectedMinLength += payloadLength;
			} else {
				expectedMinLength += extraPayloadLengthBytes;
			}
			if (N < expectedMinLength) {
				return null;
			}
			if (extraPayloadLengthBytes != 0) {
				var extendedSize =
								new BigInteger(extractBytes(bufferContext, METADATA_IN_BYTES, METADATA_IN_BYTES + extraPayloadLengthBytes));
				expectedMinLength += extendedSize.intValue();
				payloadLength = extendedSize.intValue();
			}
			if (N < expectedMinLength) {
				return null;
			}
			var maskingKey = isMasked
							? extractBytes(bufferContext,
							METADATA_IN_BYTES + extraPayloadLengthBytes,
							METADATA_IN_BYTES + MASKING_KEY_IN_BYTES + extraPayloadLengthBytes)
							: null;

			var payloadStart = METADATA_IN_BYTES + (isMasked ? MASKING_KEY_IN_BYTES : 0) + extraPayloadLengthBytes;

			var payload = extractBytes(bufferContext, payloadStart, payloadStart + payloadLength);
			if (isMasked) {
				ByteUtils.applyMaskingKey(payload, maskingKey);
			}
			var webSocketMessage = new WebSocketMessage();
			webSocketMessage.setFin(isFin);
			webSocketMessage.setOpCode(opCode);
			webSocketMessage.setMaskingKey(maskingKey);
			webSocketMessage.setPayload(payload);
		return new Pair<>(webSocketMessage, expectedMinLength);
	}

	private int getPayloadLength(BufferContext bufferContext) {
		return bufferContext.get(1) & PAYLOAD_LENGTH_BITMASK;
	}

	private OpCode getOpCode(BufferContext bufferContext) throws ParseException {
		return OpCode.getByCode(bufferContext.get(0) & OP_CODE_BITMASK);
	}

	private boolean isFin(BufferContext bufferContext) {
		return (bufferContext.get(0) & IS_FIN_BITMASK) != 0;
	}

	private boolean isMasked(BufferContext bufferContext) {
		return (bufferContext.get(1) & IS_MASKED_BITMASK) != 0;
	}

	private byte[] extractBytes(BufferContext bufferContext, int start, int end) {
		byte[] arr = new byte[end - start];
		for (int i = start; i < end; i++) {
			arr[i - start] = bufferContext.get(i);
		}
		return arr;
	}

}
