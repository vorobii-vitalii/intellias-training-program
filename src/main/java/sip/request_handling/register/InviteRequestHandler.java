package sip.request_handling.register;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sip.AddressOfRecord;
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
import sip.request_handling.SIPRequestHandler;
import tcp.MessageSerializer;
import tcp.server.OperationType;
import tcp.server.SocketConnection;

// 24.2 Session Setup
public class InviteRequestHandler implements SIPRequestHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(InviteRequestHandler.class);
	private final BindingStorage bindingStorage;
	private final MessageSerializer messageSerializer;
	private final Via serverVia;
	private final SipURI currentSipURI;

	public InviteRequestHandler(BindingStorage bindingStorage, MessageSerializer messageSerializer, Via serverVia, SipURI currentSipURI) {
		this.bindingStorage = bindingStorage;
		this.messageSerializer = messageSerializer;
		this.serverVia = serverVia;
		this.currentSipURI = currentSipURI;
	}

	// AddressOfRecord[name=Anonymous, sipURI=FullSipURI[protocol=sip, credentials=Credentials[username=John, password=null], address=Address[host=0.0.0.0, port=5060], uriParameters={}, queryParameters={}], parameters={}]

	// AddressOfRecord[name=Anonymous, sipURI=FullSipURI[protocol=sip, credentials=Credentials[username=John, password=null], address=Address[host=0.0.0.0, port=5068], uriParameters={}, queryParameters={}], parameters={}]

	@Override
	public void processRequest(SipRequest sipRequest, SocketConnection socketConnection) {
		var toAddressOfRecord = sipRequest.headers().getTo();
		// Assume address of record host = current host for now...
		var connections = bindingStorage.getConnectionsByAddressOfRecord(toAddressOfRecord.toCanonicalForm());
		// Such client is not connected to server, assume if not connected = still exists.
		if (connections.isEmpty()) {
			LOGGER.warn("Sending not found response");
			sendNotFoundResponse(sipRequest, socketConnection);
			return;
		}
		LOGGER.warn("Sending invites");
		for (SocketConnection connection : connections) {
			sendInvite(sipRequest, connection);
		}
		LOGGER.warn("Sending trying...");
		sendTryingResponse(sipRequest, socketConnection);
	}

	@Override
	public String getHandledRequestType() {
		return "INVITE";
	}

	private void sendInvite(SipRequest originalRequest, SocketConnection clientToCall) {
		var sipRequestHeaders = new SipRequestHeaders();
		sipRequestHeaders.addVia(serverVia);
		for (Via via : originalRequest.headers().getViaList()) {
			sipRequestHeaders.addVia(via.normalize());
		}
		sipRequestHeaders.setFrom(originalRequest.headers().getFrom());
		sipRequestHeaders.setTo(originalRequest.headers().getTo());
		sipRequestHeaders.setContactList(calculateContactSet(originalRequest));
		sipRequestHeaders.setCallId(originalRequest.headers().getCallId());
		sipRequestHeaders.setMaxForwards(originalRequest.headers().getMaxForwards() - 1);
		sipRequestHeaders.setCommandSequence(originalRequest.headers().getCommandSequence());
		var sipResponse = new SipRequest(
				new SipRequestLine("INVITE", originalRequest.requestLine().requestURI(), originalRequest.requestLine().version()),
				sipRequestHeaders,
				originalRequest.payload()
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
		sipResponseHeaders.setFrom(sipRequest.headers().getFrom());
		sipResponseHeaders.setTo(sipRequest.headers().getTo()
				.addParam("tag", UUID.nameUUIDFromBytes(sipRequest.headers().getTo().sipURI().getURIAsString().getBytes()).toString())
		);
		sipResponseHeaders.setContactList(calculateContactSet(sipRequest));
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
