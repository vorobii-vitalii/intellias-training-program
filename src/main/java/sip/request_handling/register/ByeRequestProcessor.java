package sip.request_handling.register;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.jsdp.SDPException;
import sip.AddressOfRecord;
import sip.ContactSet;
import sip.SipRequest;
import sip.SipURI;
import sip.Via;
import sip.request_handling.SipRequestHandler;
import sip.request_handling.calls.CallsRepository;
import tcp.MessageSerializer;
import tcp.server.OperationType;
import tcp.server.SocketConnection;

public class ByeRequestProcessor implements SipRequestHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(ByeRequestProcessor.class);

	public static final String BYE = "BYE";

	private final CallsRepository callsRepository;
	private final BindingStorage bindingStorage;
	private final SipURI currentSipURI;
	private final Via serverVia;
	private final MessageSerializer messageSerializer;

	public ByeRequestProcessor(CallsRepository callsRepository, BindingStorage bindingStorage, SipURI currentSipURI, Via serverVia,
			MessageSerializer messageSerializer) {
		this.callsRepository = callsRepository;
		this.bindingStorage = bindingStorage;
		this.currentSipURI = currentSipURI;
		this.serverVia = serverVia;
		this.messageSerializer = messageSerializer;
	}

	@Override
	public void process(SipRequest sipRequest, SocketConnection socketConnection) {
		var toAddressOfRecord = sipRequest.headers().getTo();
		// Assume address of record host = current host for now...
		var connections = bindingStorage.getConnectionsByAddressOfRecord(toAddressOfRecord.toCanonicalForm());
		// Such client is not connected to server, assume if not connected = still exists.
		// sipp -sn branchc 0.0.0.0:5068 -t tn -max_socket 20 -trace_err
		if (connections.isEmpty()) {
			// No connections
			return;
		}
		var callId = sipRequest.headers().getCallId();
		var callDetails = callsRepository.upsert(callId);
		callsRepository.update(callId, callDetails);
		LOGGER.info("Sending invites");
		for (SocketConnection connection : connections) {
			sendBye(sipRequest, connection);
		}
		callsRepository.remove(callId);
	}

	private void sendBye(SipRequest originalRequest, SocketConnection clientToCall) {
		var requestCopy = originalRequest.replicate();
		requestCopy.headers().addViaFront(serverVia);
		requestCopy.headers().setContactList(new ContactSet(Set.of(new AddressOfRecord("", currentSipURI, Map.of()))));
		var sipResponse = new SipRequest(
				requestCopy.requestLine(),
				requestCopy.headers(),
				new byte[] {}
		);
		clientToCall.appendResponse(messageSerializer.serialize(sipResponse));
		clientToCall.changeOperation(OperationType.WRITE);
	}

	@Override
	public String getHandledType() {
		return BYE;
	}
}
