package websocket;

import util.Serializable;

import java.math.BigInteger;
import java.util.Arrays;

public class WebSocketMessage implements Serializable {
	private boolean isFin;
	private OpCode opCode;
	private boolean isMasked;
	private int payloadLength;
	private byte[] maskingKey;
	private byte[] payload;

	public boolean isFin() {
		return isFin;
	}

	public void setFin(boolean fin) {
		isFin = fin;
	}

	public OpCode getOpCode() {
		return opCode;
	}

	public void setOpCode(OpCode opCode) {
		this.opCode = opCode;
	}

	public boolean isMasked() {
		return isMasked;
	}

	public void setMasked(boolean masked) {
		isMasked = masked;
	}

	public int getPayloadLength() {
		return payloadLength;
	}

	public void setPayloadLength(int payloadLength) {
		this.payloadLength = payloadLength;
	}

	public byte[] getPayload() {
		return payload;
	}

	public void setPayload(byte[] payload) {
		this.payload = payload;
	}

	public byte[] getMaskingKey() {
		return maskingKey;
	}

	public void setMaskingKey(byte[] maskingKey) {
		this.maskingKey = maskingKey;
	}

	@Override
	public byte[] serialize() {
		byte firstByte = 0;
		byte secondByte = 0;
		byte[] payloadLengthBytes = new byte[]{};
		firstByte |= (isFin ? 1 : 0) << 7;
		firstByte |= opCode.getCode();
		secondByte |= (isMasked ? 1 : 0) << 7;
		if (payload.length <= 125) {
			secondByte |= payload.length;
		}
		else if (payload.length <= 32788 * 2 - 1) {
			secondByte |= 126;
			payloadLengthBytes = BigInteger.valueOf(payload.length).toByteArray();
		}
		else {
			secondByte |= 127;
			payloadLengthBytes = BigInteger.valueOf(payload.length).toByteArray();
		}
		byte[] arr = merge(
						new byte[]{firstByte, secondByte},
						payloadLengthBytes,
						maskingKey,
						payload
		);
		if (isMasked) {
			int payloadPos = getTotal(payloadLengthBytes, maskingKey) + 2;
			for (int i = 0; i < payload.length; i++) {
				arr[payloadPos + i] = (byte) (arr[payloadPos + i] ^ maskingKey[i % maskingKey.length]);
			}
		}
		return arr;
	}

	private int getTotal(byte[]... arrays) {
		var totalSize = 0;
		for (var byteArray : arrays) {
			if (byteArray != null) {
				totalSize += byteArray.length;
			}
		}
		return totalSize;
	}

	private byte[] merge(byte[]... byteArrays) {
		var totalSize = getTotal(byteArrays);
		var mergedArr = new byte[totalSize];
		var current = 0;
		for (var byteArray : byteArrays) {
			if (byteArray != null) {
				System.arraycopy(byteArray, 0, mergedArr, current, byteArray.length);
				current += byteArray.length;
			}
		}
		return mergedArr;
	}

	@Override
	public String toString() {
		return "WebSocketMessage{" +
						"isFin=" + isFin +
						", opCode=" + opCode +
						", isMasked=" + isMasked +
						", payloadLength=" + payloadLength +
						", maskingKey=" + Arrays.toString(maskingKey) +
						", payload=" + Arrays.toString(payload) +
						'}';
	}
}
