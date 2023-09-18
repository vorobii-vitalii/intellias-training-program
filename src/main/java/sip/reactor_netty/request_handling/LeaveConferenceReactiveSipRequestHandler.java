package sip.reactor_netty.request_handling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import document_editor.netty_reactor.request_handling.ReactiveMessageHandler;
import reactor.core.publisher.Flux;
import sip.FullSipURI;
import sip.SipMessage;
import sip.SipRequest;
import sip.reactor_netty.WSOutbound;
import sip.reactor_netty.service.ReactiveConferenceSubscribersContext;
import sip.request_handling.invite.ConferenceDisconnectRequest;
import sip.request_handling.invite.MediaConferenceService;

public class LeaveConferenceReactiveSipRequestHandler implements ReactiveMessageHandler<String, SipRequest, SipMessage, WSOutbound> {
	private static final Logger LOGGER = LoggerFactory.getLogger(LeaveConferenceReactiveSipRequestHandler.class);
	private static final String BYE = "BYE";
	private static final String DISAMBIGUATOR_HEADER = "X-Disambiguator".toLowerCase();
	private static final String DEFAULT_DISAMBIGUATOR = "";

	private final MediaConferenceService mediaConferenceService;
	private final ReactiveConferenceSubscribersContext reactiveConferenceSubscribersContext;

	public LeaveConferenceReactiveSipRequestHandler(MediaConferenceService mediaConferenceService,
			ReactiveConferenceSubscribersContext reactiveConferenceSubscribersContext) {
		this.mediaConferenceService = mediaConferenceService;
		this.reactiveConferenceSubscribersContext = reactiveConferenceSubscribersContext;
	}

	@Override
	public Flux<? extends SipMessage> handleMessage(SipRequest sipRequest, WSOutbound context) {
		var conferenceId = getConferenceId(sipRequest);
		var disconnectRequest = createDisconnectRequest(sipRequest, conferenceId);
		LOGGER.info("Doing disconnect from conference {}", disconnectRequest);
		return mediaConferenceService.disconnectFromConference(disconnectRequest)
				.doOnSuccess(r -> {
					LOGGER.info("Client disconnected from conference {}, sending notification", conferenceId);
					reactiveConferenceSubscribersContext.notifyParticipantsChanged(conferenceId);
				})
				.thenMany(Flux.empty());
	}

	private ConferenceDisconnectRequest createDisconnectRequest(SipRequest sipRequest, String conferenceId) {
		return new ConferenceDisconnectRequest(
				conferenceId,
				sipRequest.headers().getFrom().toCanonicalForm().sipURI(),
				getDisambiguator(sipRequest)
		);
	}

	@Override
	public String getHandledMessageType() {
		return BYE;
	}

	private String getDisambiguator(SipRequest sipRequest) {
		return sipRequest.headers()
				.getExtensionHeaderValue(DISAMBIGUATOR_HEADER).map(v -> v.get(0))
				.orElse(DEFAULT_DISAMBIGUATOR);
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
