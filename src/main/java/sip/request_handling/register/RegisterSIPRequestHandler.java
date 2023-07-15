package sip.request_handling.register;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sip.AddressOfRecord;
import sip.ContactAny;
import sip.ContactSet;
import sip.SipRequest;
import sip.SipResponse;
import sip.SipResponseHeaders;
import sip.SipResponseLine;
import sip.SipStatusCode;
import sip.Via;
import sip.request_handling.SIPRequestHandler;
import tcp.MessageSerializer;
import tcp.server.OperationType;
import tcp.server.SocketConnection;

public class RegisterSIPRequestHandler implements SIPRequestHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(RegisterSIPRequestHandler.class);
	private static final int DEFAULT_EXPIRATION = 3600;

	public static final Integer REMOVE_BINDINGS = 0;

	private final MessageSerializer messageSerializer;
	private final BindingStorage bindingStorage;
	private final Via serverVia;

	public RegisterSIPRequestHandler(MessageSerializer messageSerializer, BindingStorage bindingStorage, Via serverVia) {
		this.messageSerializer = messageSerializer;
		this.bindingStorage = bindingStorage;
		this.serverVia = serverVia;
	}

	@Override
	public void processRequest(SipRequest sipRequest, SocketConnection socketConnection) {
		// Index for list of bindings
		var addressOfRecord = sipRequest.headers().getTo().toCanonicalForm();
		var contactList = sipRequest.headers().getContactList();
		if (contactList == null) {
			return;
		}
		// Unintuitive but REGISTER reqeust with Contact = * means unregister...
		if (contactList instanceof ContactAny) {
			var expiresValue = sipRequest.headers().getExpires();
			if (!REMOVE_BINDINGS.equals(expiresValue)) {
				LOGGER.error("""
						If the request has additional Contact
						         fields or an expiration time other than zero, the request is
						         invalid, and the server MUST return a 400 (Invalid Request)""");
				socketConnection.appendResponse(messageSerializer.serialize(createBadRequestResponse(sipRequest)));
				socketConnection.changeOperation(OperationType.WRITE);
				return;
			}
			bindingStorage.removeBindingsByAddressOfRecord(addressOfRecord);
		}
		else {
			var toRegisterAddressOfRecords = ((ContactSet) contactList).allowedAddressOfRecords();
			var newBindings = toRegisterAddressOfRecords.stream()
					.map(r -> new CreateBinding(r, sipRequest.headers().getCallId(),
							sipRequest.headers().getCommandSequence().sequenceNumber()))
					.collect(Collectors.toList());
			var expiration = Optional.ofNullable(sipRequest.headers().getExpires()).orElse(DEFAULT_EXPIRATION);
			bindingStorage.addBindings(socketConnection, addressOfRecord, newBindings, expiration);
			socketConnection.appendResponse(messageSerializer.serialize(createOKResponse(sipRequest, addressOfRecord)));
			socketConnection.changeOperation(OperationType.WRITE);
		}

	}

	private SipResponse createOKResponse(SipRequest sipRequest, AddressOfRecord addressOfRecord) {
		var sipResponseHeaders = new SipResponseHeaders();
		for (Via via : sipRequest.headers().getViaList()) {
			sipResponseHeaders.addVia(via.normalize());
		}
		sipResponseHeaders.addVia(serverVia);
		sipResponseHeaders.setFrom(sipRequest.headers().getFrom());
		sipResponseHeaders.setTo(sipRequest.headers().getTo()
				.addParam("tag", UUID.nameUUIDFromBytes(sipRequest.headers().getTo().sipURI().getURIAsString().getBytes()).toString())
		);
		sipResponseHeaders.setContactList(new ContactSet(
				bindingStorage.getCurrentBindings(addressOfRecord)
		));
		sipResponseHeaders.setCommandSequence(sipRequest.headers().getCommandSequence());
		return new SipResponse(
				new SipResponseLine(
						sipRequest.requestLine().version(),
						new SipStatusCode(200),
						"OK"
				),
				sipResponseHeaders,
				new byte[] {}
		);
	}

	private static SipResponse createBadRequestResponse(SipRequest sipRequest) {
		return new SipResponse(
				new SipResponseLine(
						sipRequest.requestLine().version(),
						new SipStatusCode(400),
						"Invalid request..."
				),
				new SipResponseHeaders(),
				new byte[] {}
		);
	}

	@Override
	public String getHandledRequestType() {
		return "REGISTER";
	}

}
