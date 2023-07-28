package sip.request_handling.invite;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.jsdp.SDPException;
import net.sourceforge.jsdp.SDPFactory;
import net.sourceforge.jsdp.SessionDescription;
import sip.ContactSet;
import sip.SipRequest;
import sip.SipResponse;
import sip.SipResponseLine;
import sip.SipStatusCode;
import sip.SipURI;
import sip.request_handling.SDPMediaAddressProcessor;
import sip.request_handling.SipRequestHandler;
import sip.request_handling.Updater;
import sip.request_handling.calls.CallsRepository;
import sip.request_handling.register.BindingStorage;
import tcp.MessageSerializer;
import tcp.MessageSerializerImpl;
import tcp.server.OperationType;
import tcp.server.SocketConnection;

// 24.2 Session Setup
public class InviteRequestHandler implements SipRequestHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(InviteRequestHandler.class);
	private static final String INVITE = "INVITE";
	private static final int NOT_FOUND = 404;

	private final BindingStorage bindingStorage;
	private final MessageSerializer messageSerializer;
	private final Collection<SDPMediaAddressProcessor> sdpMediaAddressProcessors;
	private final CallsRepository callsRepository;
	private final Updater<SipRequest> sipRequestUpdater;

	public InviteRequestHandler(
			BindingStorage bindingStorage,
			MessageSerializer messageSerializer,
			Collection<SDPMediaAddressProcessor> sdpMediaAddressProcessors,
			CallsRepository callsRepository,
			Updater<SipRequest> sipRequestUpdater
	) {
		this.bindingStorage = bindingStorage;
		this.messageSerializer = messageSerializer;
		this.sdpMediaAddressProcessors = sdpMediaAddressProcessors;
		this.callsRepository = callsRepository;
		this.sipRequestUpdater = sipRequestUpdater;
	}

	@Override
	public void process(SipRequest sipRequest, SocketConnection socketConnection) {
		var toAddressOfRecord = sipRequest.headers().getTo();
		var connections = bindingStorage.getConnectionsByAddressOfRecord(toAddressOfRecord.toCanonicalForm());
		if (connections.isEmpty()) {
			LOGGER.warn("Sending not found response");
			sendNotFoundResponse(sipRequest, socketConnection);
			return;
		}
		try {
			var callId = sipRequest.headers().getCallId();
			var callDetails = callsRepository.upsert(callId).setCallerContact(calculateCallerContact(sipRequest));
			callDetails.addConnection(socketConnection);
			var sessionDescription = SDPFactory.parseSessionDescription(new String(sipRequest.payload(), StandardCharsets.UTF_8));
			LOGGER.info("SDP before {}", sessionDescription);
			for (var sdpMediaAddressProcessor : sdpMediaAddressProcessors) {
				var mediaAddress = sdpMediaAddressProcessor.getMediaAddress(sessionDescription);
				if (mediaAddress != null) {
					callDetails.addMediaMapping(mediaAddress.mediaAddressType(), mediaAddress.originalAddress());
				}
			}
			for (var sdpMediaAddressProcessor : sdpMediaAddressProcessors) {
				sdpMediaAddressProcessor.update(sessionDescription);
			}
			LOGGER.info("SDP after {}", sessionDescription);
			callsRepository.update(callId, callDetails);
			LOGGER.info("Sending invites");
			for (SocketConnection connection : connections) {
				sendInvite(sipRequest, connection, sessionDescription);
			}
		}
		catch (SDPException e) {
			LOGGER.error("Failed to parse SDP message", e);
			throw new RuntimeException(e);
		}
	}

	private static SipURI calculateCallerContact(SipRequest sipRequest) {
		var contactList = sipRequest.headers().getContactList();
		if (contactList instanceof ContactSet contactSet) {
			return contactSet.allowedAddressOfRecords()
					.stream()
					.findFirst()
					.orElseThrow(() -> new IllegalStateException("No Contact was provided " + sipRequest))
					.sipURI();
		}
		throw new IllegalStateException("Contact list in unexpected format..." + contactList);
	}


	private void sendInvite(SipRequest originalRequest, SocketConnection clientToCall, SessionDescription sessionDescription) {
		var requestCopy = sipRequestUpdater.update(originalRequest.replicate());
		var serializedSDP = sessionDescription.toString().getBytes(StandardCharsets.UTF_8);
		var sipResponse = new SipRequest(requestCopy.requestLine(), requestCopy.headers(), serializedSDP);
		clientToCall.appendResponse(messageSerializer.serialize(sipResponse));
		clientToCall.changeOperation(OperationType.WRITE);
	}

	private void sendNotFoundResponse(SipRequest sipRequest, SocketConnection socketConnection) {
		var sipResponseHeaders = sipRequest.headers().toResponseHeaders();
		var responseLine = new SipResponseLine(sipRequest.requestLine().version(), new SipStatusCode(NOT_FOUND), "Not found");
		var sipResponse = new SipResponse(responseLine, sipResponseHeaders, new byte[] {});
		socketConnection.appendResponse(messageSerializer.serialize(sipResponse));
		socketConnection.changeOperation(OperationType.WRITE);
	}

	@Override
	public Set<String> getHandledTypes() {
		return Set.of(INVITE);
	}
}
