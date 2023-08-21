package sip.reactor_netty.request_handling;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import document_editor.netty_reactor.request_handling.ReactiveRequestHandler;
import reactor.core.publisher.Flux;
import sip.FullSipURI;
import sip.SipMediaType;
import sip.SipMessage;
import sip.SipRequest;
import sip.SipRequestHeaders;
import sip.reactor_netty.WSOutbound;
import sip.request_handling.DialogRequest;
import sip.request_handling.DialogService;
import sip.request_handling.SipSessionDescription;
import sip.request_handling.invite.ConferenceJoinRequest;
import sip.request_handling.invite.ConferenceSubscribersContext;
import sip.request_handling.invite.MediaConferenceService;
import sip.request_handling.invite.Mode;

public class JoinConferenceReactiveSipRequestHandler implements ReactiveRequestHandler<String, SipRequest, SipMessage, WSOutbound> {
	public static final String INVITE = "INVITE";
	public static final String APPLICATION_SDP = "application/sdp";
	public static final SipMediaType SDP_MEDIA_TYPE = SipMediaType.parse(APPLICATION_SDP);
	public static final String DISAMBIGUATOR_HEADER = "X-Disambiguator".toLowerCase();
	public static final String DEFAULT_DISAMBIGUATOR = "";
	public static final String RECEIVING_HEADER = "X-Receiving".toLowerCase();
	public static final String INFO_METHOD = "INFO";
	public static final SipMediaType JSON_MEDIA_TYPE = SipMediaType.parse("application/json");
	private static final Logger LOGGER = LoggerFactory.getLogger(JoinConferenceReactiveSipRequestHandler.class);

	private final MediaConferenceService mediaConferenceService;
	private final ConferenceSubscribersContext conferenceSubscribersContext;
	private final DialogService dialogService;

	public JoinConferenceReactiveSipRequestHandler(
			MediaConferenceService mediaConferenceService,
			ConferenceSubscribersContext conferenceSubscribersContext,
			DialogService dialogService
	) {
		this.mediaConferenceService = mediaConferenceService;
		this.conferenceSubscribersContext = conferenceSubscribersContext;
		this.dialogService = dialogService;
	}

	@Override
	public Flux<? extends SipMessage> handleRequest(SipRequest sipRequest, WSOutbound context) {
		LOGGER.info("Received request to join conference {}", sipRequest);
		var conferenceId = getConferenceId(sipRequest);
		var callId = sipRequest.headers().getCallId();
		var joinResponse = mediaConferenceService.connectToConferenceReactive(createConferenceJoinRequest(sipRequest, conferenceId));
		conferenceSubscribersContext.onParticipantsUpdate(conferenceId);
		var sipSessionDescription = new SipSessionDescription(joinResponse.sdpAnswer(), SDP_MEDIA_TYPE);
		return Flux.<SipMessage> just(dialogService.establishDialog(sipRequest, sipSessionDescription))
				.concatWith(joinResponse.iceCandidates()
								.map(candidate -> {
									var overrideHeaders = new SipRequestHeaders();
									overrideHeaders.setContentType(JSON_MEDIA_TYPE);
									return dialogService.makeDialogRequest(
											new DialogRequest(
													callId,
													INFO_METHOD,
													overrideHeaders,
													candidate.getBytes(StandardCharsets.UTF_8)
											));
								}));
	}

	@Override
	public String getHandledRequestType() {
		return INVITE;
	}

	@Override
	public boolean canHandle(SipRequest sipRequest) {
		return mediaConferenceService.isConference(getConferenceId(sipRequest));
	}

	private String getConferenceId(SipRequest sipRequest) {
		var sipURI = (FullSipURI) sipRequest.requestLine().requestURI();
		return sipURI.credentials().username();
	}

	private ConferenceJoinRequest createConferenceJoinRequest(SipRequest sipRequest, String conferenceId) {
		return new ConferenceJoinRequest(
				conferenceId,
				sipRequest.headers().getFrom().toCanonicalForm().sipURI(),
				new String(sipRequest.payload(), StandardCharsets.UTF_8),
				getDisambiguator(sipRequest),
				new Mode(isReceiving(sipRequest), true)
		);
	}

	private boolean isReceiving(SipRequest sipRequest) {
		var v = sipRequest.headers().getBooleanExtensionHeader(RECEIVING_HEADER);
		return v == null || v;
	}

	private String getDisambiguator(SipRequest sipRequest) {
		return sipRequest.headers()
				.getExtensionHeaderValue(DISAMBIGUATOR_HEADER).map(v -> v.get(0))
				.orElse(DEFAULT_DISAMBIGUATOR);
	}

}
