package sip.request_handling.calls;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import sip.Address;
import sip.SipURI;
import tcp.server.SocketConnection;

public record CallDetails(
		Map<String, Set<Address>> addressesByMediaAddressType,
		@Deprecated Set<SocketConnection> connectionsInvolved,
		@Nullable SipURI callerContact
) {
	public void addMediaMapping(String mediaAddressType, Address address) {
		addressesByMediaAddressType.computeIfAbsent(mediaAddressType, s -> new HashSet<>());
		addressesByMediaAddressType.get(mediaAddressType).add(address);
	}

	public void addConnection(SocketConnection socketConnection) {
		connectionsInvolved.add(socketConnection);
	}

	public CallDetails setCallerContact(SipURI callerContact) {
		return new CallDetails(addressesByMediaAddressType, connectionsInvolved, callerContact);
	}

}
