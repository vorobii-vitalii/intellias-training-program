package websocket.domain;

import util.Serializable;

import java.math.BigInteger;
import java.util.Arrays;

public class WebSocketMessage implements Serializable {

	private boolean isFin;
	private OpCode opCode;
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
		secondByte |= (maskingKey != null ? 1 : 0) << 7;
		System.out.println("payload.length = " + payload.length);
		if (payload.length <= 125) {
			secondByte |= payload.length;
		}
		else if (payload.length <= Math.pow(2, 16) - 1) {
			secondByte |= 126;
			payloadLengthBytes = pad(BigInteger.valueOf(payload.length).toByteArray(), 2);
		}
		else {
			secondByte |= 127;
			payloadLengthBytes = pad(BigInteger.valueOf(payload.length).toByteArray(), 8);
		}
//		System.out.println("payload length bytes = " + Arrays.toString(payloadLengthBytes));
//		if (payloadLengthBytes != null) {
//			System.out.println("reverse = " + new BigInteger(payloadLengthBytes));
//		}
		byte[] arr = merge(
						new byte[]{firstByte, secondByte},
						payloadLengthBytes,
						maskingKey,
						payload
		);
		if (maskingKey != null) {
			int payloadPos = getTotal(payloadLengthBytes, maskingKey) + 2;
			for (int i = 0; i < payload.length; i++) {
				arr[payloadPos + i] = (byte) (arr[payloadPos + i] ^ maskingKey[i % maskingKey.length]);
			}
		}
		return arr;
	}

	private byte[] pad(byte[] arr, int k) {
		System.out.println("Pad " + Arrays.toString(arr) + " k = " + k);
		byte[] res = new byte[k];
		for (int i = 0; i < arr.length; i++) {
			if (arr[arr.length - i - 1] != 0) {
				res[res.length - i - 1] = arr[arr.length - i - 1];
			}
		}
		return res;
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
						", maskingKey=" + Arrays.toString(maskingKey) +
						", payload=" + Arrays.toString(payload) +
						'}';
	}
}
