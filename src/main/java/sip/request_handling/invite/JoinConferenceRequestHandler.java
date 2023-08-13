package sip.request_handling.invite;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import serialization.Serializer;
import sip.ContactSet;
import sip.FullSipURI;
import sip.SipMediaType;
import sip.SipRequest;
import sip.SipResponse;
import sip.SipResponseLine;
import sip.SipStatusCode;
import sip.request_handling.SipRequestHandler;
import tcp.MessageSerializer;
import tcp.server.OperationType;
import tcp.server.SocketConnection;

public class JoinConferenceRequestHandler implements SipRequestHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(JoinConferenceRequestHandler.class);

	public static final String INVITE = "INVITE";
	public static final String APPLICATION_SDP = "application/sdp";
	public static final String DISAMBIGUATOR_HEADER = "X-Disambiguator";
	private final MediaConferenceService mediaConferenceService;
	private final MessageSerializer messageSerializer;
	private final ConferenceSubscribersContext conferenceSubscribersContext;
	private final Serializer serializer;

	public JoinConferenceRequestHandler(
			MediaConferenceService mediaConferenceService,
			MessageSerializer messageSerializer,
			ConferenceSubscribersContext conferenceSubscribersContext, Serializer serializer) {
		this.mediaConferenceService = mediaConferenceService;
		this.messageSerializer = messageSerializer;
		this.conferenceSubscribersContext = conferenceSubscribersContext;
		this.serializer = serializer;
	}

	@Override
	public void process(SipRequest sipRequest, SocketConnection socketConnection) {
		LOGGER.info("Received request to join conference {}", sipRequest);
		var conferenceId = getConferenceId(sipRequest);
		var sdpOffer = new String(sipRequest.payload(), StandardCharsets.UTF_8);
		LOGGER.info("ConferenceId = {} SDP offer = {}", conferenceId, sdpOffer);

		var sdpAnswer = mediaConferenceService.connectToConference(
				new ConferenceJoinRequest(
					conferenceId,
					sipRequest.headers().getFrom().toCanonicalForm().sipURI(),
					sdpOffer,
					getDisambiguator(sipRequest),
					new Mode(true, true) // TODO: Fix
			 	));
		conferenceSubscribersContext.onParticipantsUpdate(conferenceId);
		var responseHeaders = sipRequest.headers().toResponseHeaders();
		var tag = UUID.nameUUIDFromBytes(responseHeaders.getTo().toString().getBytes(StandardCharsets.UTF_8)).toString();
		responseHeaders.setTo(responseHeaders.getTo().addParam("tag", tag));
		responseHeaders.setContentType(SipMediaType.parse(APPLICATION_SDP));
		responseHeaders.setContactList(new ContactSet(Set.of(sipRequest.headers().getTo())));
		socketConnection.appendResponse(messageSerializer.serialize(
				new SipResponse(
						new SipResponseLine(sipRequest.requestLine().version(), new SipStatusCode(200), "OK"),
						responseHeaders,
						sdpAnswer.getBytes(StandardCharsets.UTF_8)
				)));
		socketConnection.changeOperation(OperationType.WRITE);
	}

	private String getDisambiguator(SipRequest sipRequest) {
		return sipRequest.headers().getExtensionHeaderValue(DISAMBIGUATOR_HEADER).map(v -> v.get(0)).orElse("");
	}

	@Override
	public Set<String> getHandledTypes() {
		return Set.of(INVITE);
	}

	@Override
	public boolean canHandle(SipRequest sipRequest) {
		return mediaConferenceService.isConference(getConferenceId(sipRequest));
	}

	private String getConferenceId(SipRequest sipRequest) {
		var sipURI = (FullSipURI) sipRequest.requestLine().requestURI();
		return sipURI.credentials().username();
	}

}
