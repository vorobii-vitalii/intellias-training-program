package sip.request_handling.invite;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import serialization.Serializer;
import sip.FullSipURI;
import sip.SipRequest;
import sip.SipResponse;
import sip.SipResponseHeaders;
import sip.SipResponseLine;
import sip.SipStatusCode;
import sip.SipVersion;
import sip.Via;
import sip.request_handling.SipRequestHandler;
import tcp.MessageSerializer;
import tcp.server.OperationType;
import tcp.server.SocketConnection;

public class SubscribeRequestHandler implements SipRequestHandler {
	private static final String SUBSCRIBE = "SUBSCRIBE";

	private final ConferenceSubscribersContext conferenceSubscribersContext;
	private final MessageSerializer messageSerializer;
	// TODO: Create smth more generic like (Initial notify request creator?)
	private final MediaConferenceService mediaConferenceService;
	private final Serializer serializer;

	public SubscribeRequestHandler(ConferenceSubscribersContext conferenceSubscribersContext, MessageSerializer messageSerializer,
			MediaConferenceService mediaConferenceService, Serializer serializer) {
		this.conferenceSubscribersContext = conferenceSubscribersContext;
		this.messageSerializer = messageSerializer;
		this.mediaConferenceService = mediaConferenceService;
		this.serializer = serializer;
	}

	@Override
	public void process(SipRequest message, SocketConnection socketConnection) {
		conferenceSubscribersContext.addSubscriber(getConferenceId(message), message, socketConnection);
		var responseLine = new SipResponseLine(new SipVersion(2, 0), new SipStatusCode(202), "OK");
		var responseHeaders = new SipResponseHeaders();
		for (Via via : message.headers().getViaList()) {
			responseHeaders.addVia(via);
		}
		responseHeaders.setFrom(message.headers().getFrom());
		responseHeaders.setTo(message.headers().getTo());
		responseHeaders.setCallId(message.headers().getCallId());
		responseHeaders.setCommandSequence(message.headers().getCommandSequence());
		responseHeaders.addHeader("Expires", String.valueOf(message.headers().getExpires()));
		responseHeaders.setContactList(message.headers().getContactList());
		var sipResponse = new SipResponse(responseLine, responseHeaders, new byte[] {});
		socketConnection.appendResponse(messageSerializer.serialize(sipResponse));
		socketConnection.changeOperation(OperationType.WRITE);
		conferenceSubscribersContext.onParticipantsUpdate(getConferenceId(message));
	}

	@Override
	public Set<String> getHandledTypes() {
		return Set.of(SUBSCRIBE);
	}

	private String getConferenceId(SipRequest sipRequest) {
		var sipURI = (FullSipURI) sipRequest.requestLine().requestURI();
		return sipURI.credentials().username();
	}

}
