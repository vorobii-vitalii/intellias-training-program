package sip.request_handling.register;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sip.AddressOfRecord;
import sip.ContactSet;
import sip.SipRequest;
import sip.SipResponse;
import sip.SipResponseHeaders;
import sip.SipResponseLine;
import sip.SipStatusCode;
import sip.SipURI;
import sip.Via;
import sip.request_handling.SipRequestHandler;
import sip.request_handling.calls.CallDetails;
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
	private final Via serverVia;
	private final SipURI currentSipURI;
	private final CallsRepository callsRepository;
	private final MediaCallInitiator mediaCallInitiator;

	public AckRequestHandler(BindingStorage bindingStorage, MessageSerializer messageSerializer, Via serverVia,
			SipURI currentSipURI, CallsRepository callsRepository, MediaCallInitiator mediaCallInitiator) {
		this.bindingStorage = bindingStorage;
		this.messageSerializer = messageSerializer;
		this.serverVia = serverVia;
		this.currentSipURI = currentSipURI;
		this.callsRepository = callsRepository;
		this.mediaCallInitiator = mediaCallInitiator;
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
		LOGGER.info("Sending ACKs");
		for (SocketConnection connection : connections) {
			sendAck(sipRequest, connection);
		}
		// Create media mapping
		final CallDetails callDetails = callsRepository.upsert(sipRequest.headers().getCallId());
		LOGGER.info("Initiating media sesssion for callDetails = {}", callDetails);
		mediaCallInitiator.initiate(callDetails);
	}

	private void sendAck(SipRequest originalRequest, SocketConnection connection) {
		// prototype pattern
		var requestCopy = originalRequest.replicate();
		requestCopy.headers().addViaFront(serverVia);
		requestCopy.headers().setContactList(new ContactSet(Set.of(new AddressOfRecord("", currentSipURI, Map.of()))));
		connection.appendResponse(messageSerializer.serialize(requestCopy));
		connection.changeOperation(OperationType.WRITE);
	}

	private void sendNotFoundResponse(SipRequest sipRequest, SocketConnection socketConnection) {
		var sipResponseHeaders = new SipResponseHeaders();
		sipResponseHeaders.addVia(serverVia);
		for (Via via : sipRequest.headers().getViaList()) {
			sipResponseHeaders.addVia(via.normalize());
		}
		sipResponseHeaders.setFrom(sipRequest.headers().getFrom());
		sipResponseHeaders.setTo(sipRequest.headers().getTo());
				sipResponseHeaders.setContactList(sipRequest.headers().getContactList());
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

	@Override
	public String getHandledType() {
		return ACK;
	}
}
