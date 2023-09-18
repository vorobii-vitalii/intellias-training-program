package sip.request_handling.register;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sip.SipRequest;
import sip.request_handling.SipRequestHandler;
import sip.request_handling.Updater;
import sip.request_handling.calls.CallState;
import sip.request_handling.calls.CallsRepository;
import tcp.MessageSerializer;
import tcp.server.OperationType;
import tcp.server.SocketConnection;

public class ByeRequestProcessor implements SipRequestHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(ByeRequestProcessor.class);

	public static final String BYE = "BYE";
	public static final String CANCEL = "CANCEL";

	private final CallsRepository callsRepository;
	private final BindingStorage bindingStorage;
	private final MessageSerializer messageSerializer;
	private final Updater<SipRequest> sipRequestUpdater;

	public ByeRequestProcessor(CallsRepository callsRepository, BindingStorage bindingStorage,
			MessageSerializer messageSerializer, Updater<SipRequest> sipRequestUpdater) {
		this.callsRepository = callsRepository;
		this.bindingStorage = bindingStorage;
		this.messageSerializer = messageSerializer;
		this.sipRequestUpdater = sipRequestUpdater;
	}

	@Override
	public void process(SipRequest sipRequest, SocketConnection socketConnection) {
		var toAddressOfRecord = sipRequest.headers().getTo();
		var connections = bindingStorage.getConnectionsByAddressOfRecord(toAddressOfRecord.toCanonicalForm());
		if (connections.isEmpty()) {
			return;
		}
		var callId = sipRequest.headers().getCallId();
		var callDetails = callsRepository.upsert(callId);
		LOGGER.info("Sending BYE messages...");
		for (SocketConnection connection : connections) {
			sendBye(sipRequest, connection);
		}
		callsRepository.update(callId, callDetails.changeCallState(CallState.CANCELLED));
	}

	private void sendBye(SipRequest originalRequest, SocketConnection clientToCall) {
		var requestCopy = sipRequestUpdater.update(originalRequest.replicate());
		clientToCall.appendResponse(messageSerializer.serialize(requestCopy));
		clientToCall.changeOperation(OperationType.WRITE);
	}

	@Override
	public Set<String> getHandledTypes() {
		return Set.of(BYE, CANCEL);
	}
}
