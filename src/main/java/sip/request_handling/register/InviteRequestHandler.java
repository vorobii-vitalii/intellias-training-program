package sip.request_handling.register;

import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.jsdp.SDPException;
import net.sourceforge.jsdp.SDPFactory;
import net.sourceforge.jsdp.SessionDescription;
import sip.AddressOfRecord;
import sip.ContactList;
import sip.ContactSet;
import sip.SipRequest;
import sip.SipRequestHeaders;
import sip.SipRequestLine;
import sip.SipResponse;
import sip.SipResponseHeaders;
import sip.SipResponseLine;
import sip.SipStatusCode;
import sip.SipURI;
import sip.Via;
import sip.request_handling.SDPMediaAddressProcessor;
import sip.request_handling.SipMessageHandler;
import sip.request_handling.SipRequestHandler;
import sip.request_handling.calls.CallsRepository;
import tcp.MessageSerializer;
import tcp.server.OperationType;
import tcp.server.SocketConnection;

// 24.2 Session Setup
public class InviteRequestHandler implements SipRequestHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(InviteRequestHandler.class);
	public static final String INVITE = "INVITE";
	private final BindingStorage bindingStorage;
	private final MessageSerializer messageSerializer;
	private final Via serverVia;
	private final SipURI currentSipURI;
	private final Collection<SDPMediaAddressProcessor> sdpMediaAddressProcessors;
	private final CallsRepository callsRepository;

	public InviteRequestHandler(
			BindingStorage bindingStorage,
			MessageSerializer messageSerializer,
			Via serverVia,
			SipURI currentSipURI,
			Collection<SDPMediaAddressProcessor> sdpMediaAddressProcessors,
			CallsRepository callsRepository
	) {
		this.bindingStorage = bindingStorage;
		this.messageSerializer = messageSerializer;
		this.serverVia = serverVia;
		this.currentSipURI = currentSipURI;
		this.sdpMediaAddressProcessors = sdpMediaAddressProcessors;
		this.callsRepository = callsRepository;
	}

	@Override
	public void process(SipRequest sipRequest, SocketConnection socketConnection) {
		Selector selector;
		var toAddressOfRecord = sipRequest.headers().getTo();
		// Assume address of record host = current host for now...
		var connections = bindingStorage.getConnectionsByAddressOfRecord(toAddressOfRecord.toCanonicalForm());
		// Such client is not connected to server, assume if not connected = still exists.
		// sipp -sn branchc 0.0.0.0:5068 -t tn -max_socket 20 -trace_err
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
			for (var sdpMediaAddressProcessor : sdpMediaAddressProcessors) {
				var mediaAddressReplacement = sdpMediaAddressProcessor.replaceAddress(sessionDescription);
				if (mediaAddressReplacement != null) {
					callDetails.addMediaMapping(mediaAddressReplacement.mediaAddressType(), mediaAddressReplacement.originalAddress());
					sessionDescription = mediaAddressReplacement.updatedSessionDescription();
				}
			}
			callsRepository.update(callId, callDetails);
			LOGGER.info("Sending invites");
			for (SocketConnection connection : connections) {
				sendInvite(sipRequest, connection, sessionDescription);
			}
			LOGGER.info("Sending trying...");
			sendTryingResponse(sipRequest, socketConnection);
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

	@Override
	public String getHandledType() {
		return INVITE;
	}

	private void sendInvite(SipRequest originalRequest, SocketConnection clientToCall, SessionDescription sessionDescription) {
		// prototype pattern
		var sipRequestHeaders = new SipRequestHeaders();
		sipRequestHeaders.addVia(serverVia);
		for (Via via : originalRequest.headers().getViaList()) {
			sipRequestHeaders.addVia(via.normalize());
		}
		sipRequestHeaders.setFrom(originalRequest.headers().getFrom());
		sipRequestHeaders.setTo(originalRequest.headers().getTo());
		sipRequestHeaders.setContactList(calculateContactSet(originalRequest));
//		sipRequestHeaders.setContactList(originalRequest.headers().getContactList());
		sipRequestHeaders.setCallId(originalRequest.headers().getCallId());
		sipRequestHeaders.setMaxForwards(originalRequest.headers().getMaxForwards() - 1);
		sipRequestHeaders.setCommandSequence(originalRequest.headers().getCommandSequence());
		var serializedSDP = sessionDescription.toString().getBytes(StandardCharsets.UTF_8);
		var sipResponse = new SipRequest(
				new SipRequestLine(INVITE, originalRequest.requestLine().requestURI(), originalRequest.requestLine().version()),
				sipRequestHeaders,
				serializedSDP
		);
		clientToCall.appendResponse(messageSerializer.serialize(sipResponse));
		clientToCall.changeOperation(OperationType.WRITE);
	}

	private void sendTryingResponse(SipRequest sipRequest, SocketConnection socketConnection) {
		var sipResponseHeaders = new SipResponseHeaders();
		sipResponseHeaders.addVia(serverVia);
		for (Via via : sipRequest.headers().getViaList()) {
			sipResponseHeaders.addVia(via.normalize());
		}
		sipResponseHeaders.addExtensionHeader("Status", "ringing");
		sipResponseHeaders.setCommandSequence(sipRequest.headers().getCommandSequence());
		sipResponseHeaders.setMaxForwards(sipRequest.headers().getMaxForwards() - 1);
		sipResponseHeaders.setFrom(sipRequest.headers().getFrom());
		sipResponseHeaders.setTo(sipRequest.headers().getTo()
				.addParam("tag", UUID.nameUUIDFromBytes(sipRequest.headers().getTo().sipURI().getURIAsString().getBytes()).toString())
		);
		sipResponseHeaders.setContactList(calculateContactSet(sipRequest));
//		sipResponseHeaders.setContactList(sipRequest.headers().getContactList());
		var sipResponse = new SipResponse(
				new SipResponseLine(
						sipRequest.requestLine().version(),
						new SipStatusCode(100),
						"Trying..."
				),
				sipResponseHeaders,
				new byte[] {}
		);
		socketConnection.appendResponse(messageSerializer.serialize(sipResponse));
		socketConnection.changeOperation(OperationType.WRITE);
	}

	private void sendNotFoundResponse(SipRequest sipRequest, SocketConnection socketConnection) {
		var sipResponseHeaders = new SipResponseHeaders();
		sipResponseHeaders.addVia(serverVia);
		for (Via via : sipRequest.headers().getViaList()) {
			sipResponseHeaders.addVia(via.normalize());
		}
		sipResponseHeaders.setFrom(sipRequest.headers().getFrom());
		sipResponseHeaders.setTo(sipRequest.headers().getTo()
				.addParam("tag", UUID.nameUUIDFromBytes(sipRequest.headers().getTo().sipURI().getURIAsString().getBytes()).toString())
		);
		sipResponseHeaders.setContactList(calculateContactSet(sipRequest));
//		sipResponseHeaders.setContactList(sipRequest.headers().getContactList());
		var sipResponse = new SipResponse(
				new SipResponseLine(
						sipRequest.requestLine().version(),
						new SipStatusCode(404),
						"Client not found..."
				),
				sipResponseHeaders,
				new byte[] {}
		);
		socketConnection.appendResponse(messageSerializer.serialize(sipResponse));
		socketConnection.changeOperation(OperationType.WRITE);
	}

	private ContactSet calculateContactSet(SipRequest sipRequest) {
		var to = sipRequest.headers().getTo();
		return new ContactSet(Set.of(new AddressOfRecord(to.name(), currentSipURI, Map.of())));
	}

}
