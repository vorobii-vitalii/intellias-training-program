package sip.request_handling.invite;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sip.AddressOfRecord;
import sip.FullSipURI;
import sip.SipRequest;
import sip.request_handling.SipRequestHandler;
import sip.request_handling.register.ConferencesStorage;
import tcp.MessageSerializer;
import tcp.server.SocketConnection;

public class JoinConferenceRequestHandler implements SipRequestHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(JoinConferenceRequestHandler.class);

	public static final String INVITE = "INVITE";
	private final ConferencesStorage conferencesStorage;
	private final MediaConferenceService mediaConferenceService;
	private final MessageSerializer messageSerializer;

	public JoinConferenceRequestHandler(
			ConferencesStorage conferencesStorage,
			MediaConferenceService mediaConferenceService,
			MessageSerializer messageSerializer
	) {
		this.conferencesStorage = conferencesStorage;
		this.mediaConferenceService = mediaConferenceService;
		this.messageSerializer = messageSerializer;
	}

	@Override
	public void process(SipRequest sipRequest, SocketConnection socketConnection) {
		LOGGER.info("Received request to join conference {}", sipRequest);
		var conferenceId = getConferenceId(sipRequest);
		var fromAOR = sipRequest.headers().getFrom();
		var sdpOffer = new String(sipRequest.payload(), StandardCharsets.UTF_8);
		var sdpAnswer = mediaConferenceService.establishMediaSession(conferenceId, sdpOffer);
		LOGGER.info("SDP offer = {}", sdpOffer);
		LOGGER.info("SDP answer = {}", sdpAnswer);
	}

	@Override
	public Set<String> getHandledTypes() {
		return Set.of(INVITE);
	}

	@Override
	public boolean canHandle(SipRequest sipRequest) {
		return conferencesStorage.isConference(getConferenceId(sipRequest));
	}

	private String getConferenceId(SipRequest sipRequest) {
		var sipURI = (FullSipURI) sipRequest.requestLine().requestURI();
		return sipURI.credentials().username();
	}

}
