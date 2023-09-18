package sip.request_handling.register;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sip.SipRequest;
import sip.request_handling.SipRequestHandler;
import sip.request_handling.Updater;
import sip.request_handling.calls.CallState;
import sip.request_handling.calls.CallsRepository;
import sip.request_handling.media.MediaCallInitiator;
import tcp.MessageSerializer;
import tcp.server.OperationType;
import tcp.server.SocketConnection;

public class AckRequestHandler implements SipRequestHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(AckRequestHandler.class);
	public static final String ACK = "ACK";

	private final BindingStorage bindingStorage;
	private final MessageSerializer messageSerializer;
	private final CallsRepository callsRepository;
	private final MediaCallInitiator mediaCallInitiator;
	private final Updater<SipRequest> sipRequestUpdater;

	public AckRequestHandler(
			BindingStorage bindingStorage,
			MessageSerializer messageSerializer,
			CallsRepository callsRepository,
			MediaCallInitiator mediaCallInitiator,
			Updater<SipRequest> sipRequestUpdater
	) {
		this.bindingStorage = bindingStorage;
		this.messageSerializer = messageSerializer;
		this.callsRepository = callsRepository;
		this.mediaCallInitiator = mediaCallInitiator;
		this.sipRequestUpdater = sipRequestUpdater;
	}

	@Override
	public void process(SipRequest sipRequest, SocketConnection socketConnection) {
		var toAddressOfRecord = sipRequest.headers().getTo();
		var connections = bindingStorage.getConnectionsByAddressOfRecord(toAddressOfRecord.toCanonicalForm());
		if (connections.isEmpty()) {
			LOGGER.warn("Sending not found response");
			throw new IllegalStateException("No connections to send ACK to...");
		}
		LOGGER.info("Sending ACKs");
		for (SocketConnection connection : connections) {
			sendAck(sipRequest, connection);
		}
		// Create media mapping
		var callId = sipRequest.headers().getCallId();
		var callDetails = callsRepository.upsert(callId);
		LOGGER.info("Initiating media session for callDetails = {}", callDetails);
		mediaCallInitiator.initiate(callDetails);
		callsRepository.update(callId, callDetails.changeCallState(CallState.ACKED));
	}

	private void sendAck(SipRequest originalRequest, SocketConnection connection) {
		var requestCopy = sipRequestUpdater.update(originalRequest.replicate());
		connection.appendResponse(messageSerializer.serialize(requestCopy));
		connection.changeOperation(OperationType.WRITE);
	}

	@Override
	public Set<String> getHandledTypes() {
		return Set.of(ACK);
	}
}
