package sip.request_handling.calls;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import sip.Address;
import sip.SipURI;
import tcp.server.SocketConnection;

public record CallDetails(
		String callId,
		Map<String, Set<Address>> addressesByMediaAddressType,
		@Deprecated Set<SocketConnection> connectionsInvolved,
		@Deprecated @Nullable SipURI callerContact,
		CallState callState
) {
	public void addMediaMapping(String mediaAddressType, Address address) {
		addressesByMediaAddressType.computeIfAbsent(mediaAddressType, s -> new HashSet<>());
		addressesByMediaAddressType.get(mediaAddressType).add(address);
	}

	public CallDetails setCallId(String newCallId) {
		return new CallDetails(newCallId, addressesByMediaAddressType, connectionsInvolved, callerContact, callState);
	}

	public void addConnection(SocketConnection socketConnection) {
		connectionsInvolved.add(socketConnection);
	}

	public CallDetails setCallerContact(SipURI callerContact) {
		return new CallDetails(callId, addressesByMediaAddressType, connectionsInvolved, callerContact, callState);
	}

	public CallDetails changeCallState(CallState newState) {
		return new CallDetails(callId, addressesByMediaAddressType, connectionsInvolved, callerContact, newState);
	}


}
