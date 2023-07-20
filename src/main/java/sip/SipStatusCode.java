package sip;

import java.nio.ByteBuffer;

import util.Serializable;

public record SipStatusCode(int statusCode) implements Serializable {
	@Override
	public void serialize(ByteBuffer dest) {
		dest.put((byte) (statusCode / 100 + '0'));
		dest.put((byte) ((statusCode / 10) % 10 + '0'));
		dest.put((byte) (statusCode % 10 + '0'));
	}

	@Override
	public int getSize() {
		return 3;
	}

	public boolean isErroneous() {
		return statusCode >= 400;
	}

}
