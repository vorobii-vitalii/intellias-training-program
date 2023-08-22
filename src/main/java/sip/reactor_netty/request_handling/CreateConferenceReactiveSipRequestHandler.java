package sip.reactor_netty.request_handling;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import document_editor.netty_reactor.request_handling.ReactiveMessageHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sip.AddressOfRecord;
import sip.ContactSet;
import sip.Credentials;
import sip.FullSipURI;
import sip.SipMessage;
import sip.SipRequest;
import sip.SipResponse;
import sip.SipResponseLine;
import sip.SipStatusCode;
import sip.reactor_netty.WSOutbound;
import sip.request_handling.invite.MediaConferenceService;

public class CreateConferenceReactiveSipRequestHandler implements ReactiveMessageHandler<String, SipRequest, SipMessage, WSOutbound> {
	private static final int MOVED_PERMANENTLY = 301;
	private static final String REDIRECT_REASON = "Redirecting...";
	private static final String INVITE = "INVITE";
	public static final String FOCUS_PARAMETER = "ifocus";
	public static final String FOCUS_PARAMETER_VALUE = "";
	private static final Logger LOGGER = LoggerFactory.getLogger(CreateConferenceReactiveSipRequestHandler.class);

	private final Predicate<AddressOfRecord> conferenceFactoryAddressOfRecord;
	private final Supplier<String> conferenceIdGenerator;
	private final MediaConferenceService mediaConferenceService;

	public CreateConferenceReactiveSipRequestHandler(
			Predicate<AddressOfRecord> conferenceFactoryAddressOfRecord,
			Supplier<String> conferenceIdGenerator,
			MediaConferenceService mediaConferenceService
	) {
		this.conferenceFactoryAddressOfRecord = conferenceFactoryAddressOfRecord;
		this.conferenceIdGenerator = conferenceIdGenerator;
		this.mediaConferenceService = mediaConferenceService;
	}

	@Override
	public Flux<? extends SipMessage> handleMessage(SipRequest request, WSOutbound context) {
		var conferenceId = conferenceIdGenerator.get();
		return mediaConferenceService.createNewConferenceReactive(conferenceId)
				.thenMany(Mono.fromCallable(() -> {
					LOGGER.info("Conference {} created", conferenceId);
					return createRedirectResponse(request, conferenceId);
				}));
	}

	@Override
	public String getHandledMessageType() {
		return INVITE;
	}

	@Override
	public boolean canHandle(SipRequest sipRequest) {
		return conferenceFactoryAddressOfRecord.test(sipRequest.headers().getTo());
	}

	private SipResponse createRedirectResponse(SipRequest sipRequest, String conferenceId) {
		var originalTo = sipRequest.headers().getTo();
		var sipResponseLine =
				new SipResponseLine(sipRequest.requestLine().version(), new SipStatusCode(MOVED_PERMANENTLY), REDIRECT_REASON);
		var sipResponseHeaders = sipRequest.headers().toResponseHeaders();
		var sipURI = ((FullSipURI) originalTo.sipURI()).updateCredentials(new Credentials(conferenceId, null));
		sipResponseHeaders.setContactList(new ContactSet(Set.of(
				new AddressOfRecord(originalTo.name(), sipURI, Map.of(FOCUS_PARAMETER, FOCUS_PARAMETER_VALUE))
		)));
		// TODO: Take into consideration "expire" value, if not provided set reasonable default because conference cannot last forever
		return new SipResponse(sipResponseLine, sipResponseHeaders, new byte[] {});
	}

}
