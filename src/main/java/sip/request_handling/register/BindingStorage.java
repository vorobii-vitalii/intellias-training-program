package sip.request_handling.register;

import java.util.Collection;
import java.util.Set;

import sip.AddressOfRecord;
import tcp.server.SocketConnection;

public interface BindingStorage {
	Set<SocketConnection> getConnectionsByAddressOfRecord(AddressOfRecord addressOfRecord);
	void removeBindingsByAddressOfRecord(AddressOfRecord addressOfRecord);
	void addBindings(
			SocketConnection socketConnection,
			AddressOfRecord addressOfRecord,
			Collection<CreateBinding> newBindings,
			int defaultExpiration
	);
	Set<AddressOfRecord> getCurrentBindings(AddressOfRecord addressOfRecord);
}
