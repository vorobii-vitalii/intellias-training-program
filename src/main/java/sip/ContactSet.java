package sip;

import java.util.Set;

public record ContactSet(Set<AddressOfRecord> allowedAddressOfRecords) implements ContactList {
	@Override
	public boolean shouldCall(AddressOfRecord addressOfRecord) {
		return allowedAddressOfRecords.contains(addressOfRecord);
	}
}
