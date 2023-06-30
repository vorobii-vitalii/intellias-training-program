package sip;

import java.nio.ByteBuffer;

public final class ContactAny implements ContactList {
	private static final byte ALL = (byte) '*';

	@Override
	public boolean shouldCall(AddressOfRecord addressOfRecord) {
		return true;
	}

	@Override
	public void serialize(ByteBuffer dest) {
		dest.put(ALL);
	}

	@Override
	public int getSize() {
		return 1;
	}
}
