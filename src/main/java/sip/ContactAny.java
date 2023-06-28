package sip;

public final class ContactAny implements ContactList {
	@Override
	public boolean shouldCall(AddressOfRecord addressOfRecord) {
		return true;
	}
}
