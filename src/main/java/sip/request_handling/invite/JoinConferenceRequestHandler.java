package sip.request_handling.invite;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.scheduler.Schedulers;
import sip.FullSipURI;
import sip.SipMediaType;
import sip.SipRequest;
import sip.SipRequestHeaders;
import sip.request_handling.DialogRequest;
import sip.request_handling.DialogService;
import sip.request_handling.SipRequestHandler;
import sip.request_handling.SipSessionDescription;
import tcp.MessageSerializer;
import tcp.server.OperationType;
import tcp.server.SocketConnection;

public class JoinConferenceRequestHandler implements SipRequestHandler {
	public static final String INVITE = "INVITE";
	public static final String APPLICATION_SDP = "application/sdp";
	public static final SipMediaType SDP_MEDIA_TYPE = SipMediaType.parse(APPLICATION_SDP);
	public static final String DISAMBIGUATOR_HEADER = "X-Disambiguator".toLowerCase();
	public static final String DEFAULT_DISAMBIGUATOR = "";
	public static final String RECEIVING_HEADER = "X-Receiving".toLowerCase();
	public static final String INFO_METHOD = "INFO";
	private static final Logger LOGGER = LoggerFactory.getLogger(JoinConferenceRequestHandler.class);
	public static final SipMediaType JSON_MEDIA_TYPE = SipMediaType.parse("application/json");
	private final MediaConferenceService mediaConferenceService;
	private final MessageSerializer messageSerializer;
	private final ConferenceSubscribersContext conferenceSubscribersContext;
	private final DialogService<SipSessionDescription> dialogService;

	public JoinConferenceRequestHandler(
			MediaConferenceService mediaConferenceService,
			MessageSerializer messageSerializer,
			ConferenceSubscribersContext conferenceSubscribersContext,
			DialogService<SipSessionDescription> dialogService
	) {
		this.mediaConferenceService = mediaConferenceService;
		this.messageSerializer = messageSerializer;
		this.conferenceSubscribersContext = conferenceSubscribersContext;
		this.dialogService = dialogService;
	}

	@Override
	public void process(SipRequest sipRequest, SocketConnection socketConnection) {
		LOGGER.info("Received request to join conference {}", sipRequest);
		var conferenceId = getConferenceId(sipRequest);
		var joinResponse = mediaConferenceService.connectToConferenceReactive(createConferenceJoinRequest(sipRequest, conferenceId));
		conferenceSubscribersContext.onParticipantsUpdate(conferenceId);
		var sipResponse = dialogService.establishDialog(sipRequest, new SipSessionDescription(joinResponse.sdpAnswer(), SDP_MEDIA_TYPE));
		socketConnection.appendResponse(messageSerializer.serialize(sipResponse));
		socketConnection.changeOperation(OperationType.WRITE);
		var callId = sipRequest.headers().getCallId();
		joinResponse.iceCandidates()
				.subscribeOn(Schedulers.parallel())
				.subscribe(candidate -> {
					var overrideHeaders = new SipRequestHeaders();
					overrideHeaders.setContentType(JSON_MEDIA_TYPE);
					var createdRequest = dialogService.makeDialogRequest(
							new DialogRequest(callId, INFO_METHOD, overrideHeaders, candidate.getBytes(StandardCharsets.UTF_8)));
					socketConnection.appendResponse(messageSerializer.serialize(createdRequest));
					socketConnection.changeOperation(OperationType.WRITE);
				});
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

	@Override
	public Set<String> getHandledTypes() {
		return Set.of(INVITE);
	}

	@Override
	public boolean canHandle(SipRequest sipRequest) {
		return mediaConferenceService.isConference(getConferenceId(sipRequest));
	}

	private String getConferenceId(SipRequest sipRequest) {
		var sipURI = sipRequest.requestLine().requestURI();
		return sipURI.credentials().username();
	}

}
