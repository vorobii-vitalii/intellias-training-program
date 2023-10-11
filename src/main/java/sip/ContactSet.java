package sip;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Set;

public record ContactSet(Collection<AddressOfRecord> allowedAddressOfRecords) implements ContactList {

	public static final byte DELIMITER = (byte) ',';

	@Override
	public boolean shouldCall(AddressOfRecord addressOfRecord) {
		return allowedAddressOfRecords.contains(addressOfRecord);
	}

	@Override
	public void serialize(ByteBuffer dest) {
		var found = false;
		for (var allowedAddressOfRecord : allowedAddressOfRecords) {
			if (found) {
				dest.put(DELIMITER);
			}
			allowedAddressOfRecord.serialize(dest);
			found = true;
		}
	}

	@Override
	public int getSize() {
		var total = allowedAddressOfRecords.size() - 1;
		for (var record : allowedAddressOfRecords) {
			total += record.getSize();
		}
		return total;
	}
}
