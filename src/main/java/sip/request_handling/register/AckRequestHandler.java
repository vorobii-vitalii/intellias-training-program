package sip.request_handling.register;

import java.net.InetSocketAddress;
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
import sip.request_handling.SipRequestHandler;
import sip.request_handling.calls.CallsRepository;
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
	private final Collection<SDPMediaAddressProcessor> sdpMediaAddressProcessors;
	private final CallsRepository callsRepository;

	public AckRequestHandler(BindingStorage bindingStorage, MessageSerializer messageSerializer, Via serverVia,
			SipURI currentSipURI, Collection<SDPMediaAddressProcessor> sdpMediaAddressProcessors, CallsRepository callsRepository) {
		this.bindingStorage = bindingStorage;
		this.messageSerializer = messageSerializer;
		this.serverVia = serverVia;
		this.currentSipURI = currentSipURI;
		this.sdpMediaAddressProcessors = sdpMediaAddressProcessors;
		this.callsRepository = callsRepository;
	}

	@Override
	public void process(SipRequest sipRequest, SocketConnection socketConnection) {
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
		LOGGER.info("Sending ACKs");
		for (SocketConnection connection : connections) {
			sendAck(sipRequest, connection);
		}
	}

	//[20:38:47:745][Warning]Core:linphone: For payload type opus, proposed number was 96 but the remote phone answered 106

	private void sendAck(SipRequest originalRequest, SocketConnection connection) {
		// prototype pattern
		var requestCopy = originalRequest.replicate();
		requestCopy.headers().addViaFront(serverVia);
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
		sipResponseHeaders.setTo(sipRequest.headers().getTo()
//				.addParam("tag", UUID.nameUUIDFromBytes(sipRequest.headers().getTo().sipURI().getURIAsString().getBytes()).toString())
		);
//		sipResponseHeaders.setContactList(calculateContactSet(sipRequest));
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

	private ContactSet calculateContactSet(SipRequest sipRequest) {
		var to = sipRequest.headers().getTo();
		return new ContactSet(Set.of(new AddressOfRecord(to.name(), currentSipURI, Map.of())));
	}

	@Override
	public String getHandledType() {
		return ACK;
	}
}
