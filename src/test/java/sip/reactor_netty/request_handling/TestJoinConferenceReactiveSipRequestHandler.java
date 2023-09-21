package sip.reactor_netty.request_handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import sip.Address;
import sip.AddressOfRecord;
import sip.ContactAny;
import sip.Credentials;
import sip.FullSipURI;
import sip.SipMediaType;
import sip.SipRequest;
import sip.SipRequestHeaders;
import sip.SipRequestLine;
import sip.SipResponse;
import sip.SipResponseHeaders;
import sip.SipResponseLine;
import sip.SipStatusCode;
import sip.SipVersion;
import sip.reactor_netty.WSOutbound;
import sip.reactor_netty.service.impl.ReactiveConferenceSubscribersContext;
import sip.request_handling.DialogRequest;
import sip.request_handling.DialogService;
import sip.request_handling.SipSessionDescription;
import sip.request_handling.invite.ConferenceJoinRequest;
import sip.request_handling.invite.ConferenceJoinResponse;
import sip.request_handling.invite.MediaConferenceService;
import sip.request_handling.invite.Mode;

@ExtendWith(MockitoExtension.class)
class TestJoinConferenceReactiveSipRequestHandler {
	public static final String INVITE = "INVITE";
	private static final String CONFERENCE_ID = "conference-id-12523523";
	private static final Credentials CREDENTIALS = new Credentials(CONFERENCE_ID, null);
	private static final String SIP = "sip";
	private static final FullSipURI SIP_URI = new FullSipURI(SIP, CREDENTIALS, new Address("host", null),
			Map.of(), Map.of());
	private static final SipVersion SIP_VERSION = new SipVersion(1, 1);
	public static final SipResponse ESTABLISH_DIALOG_RESPONSE =
			new SipResponse(
					new SipResponseLine(SIP_VERSION, new SipStatusCode(200), "All good"),
					new SipResponseHeaders(),
					new byte[] {}
			);
	private static final String TO_USERNAME = "To";
	public static final String CALL_ID = "364337373";
	public static final String SDP_OFFER = "SDP Offer";
	public static final String SDP_ANSWER = "SDP Answer";
	public static final String DEFAULT_DISAMBIGUATOR = "";
	public static final String APPLICATION_SDP = "application/sdp";
	public static final SipMediaType SDP_MEDIA_TYPE = SipMediaType.parse(APPLICATION_SDP);
	public static final String INFO_METHOD = "INFO";
	public static final SipMediaType JSON_MEDIA_TYPE = SipMediaType.parse("application/json");
	public static final String ICE_CANDIDATE = "candidate1";
	public static final String INFO = "INFO";

	@Mock
	MediaConferenceService mediaConferenceService;
	@Mock
	ReactiveConferenceSubscribersContext reactiveConferenceSubscribersContext;
	@Mock
	DialogService<SipSessionDescription> dialogService;

	@InjectMocks
	JoinConferenceReactiveSipRequestHandler requestHandler;

	@Mock
	WSOutbound context;

	@Test
	void handleMessageNoExtensionHeadersSent() {
		var requestHeaders = new SipRequestHeaders();
		var sipRequest = new SipRequest(
				new SipRequestLine(INVITE, SIP_URI, SIP_VERSION),
				requestHeaders,
				SDP_OFFER.getBytes(StandardCharsets.UTF_8)
		);
		requestHeaders.setTo(new AddressOfRecord(TO_USERNAME, SIP_URI, Map.of()));
		requestHeaders.setFrom(new AddressOfRecord(TO_USERNAME, SIP_URI, Map.of()));
		requestHeaders.setContactList(new ContactAny());
		requestHeaders.setCallId(CALL_ID);

		when(mediaConferenceService.connectToConferenceReactive(new ConferenceJoinRequest(
				CONFERENCE_ID,
				SIP_URI.toCanonicalForm(),
				SDP_OFFER,
				DEFAULT_DISAMBIGUATOR,
				new Mode(true, true)
		))).thenReturn(new ConferenceJoinResponse(SDP_ANSWER, Flux.just(ICE_CANDIDATE)));

		when(dialogService.establishDialog(sipRequest, new SipSessionDescription(SDP_ANSWER, SDP_MEDIA_TYPE)))
				.thenReturn(ESTABLISH_DIALOG_RESPONSE);


		var infoHeaders = new SipRequestHeaders();

		var infoRequest = new SipRequest(
				new SipRequestLine(INFO, SIP_URI, SIP_VERSION),
				infoHeaders,
				ICE_CANDIDATE.getBytes(StandardCharsets.UTF_8)
		);
		infoHeaders.setTo(new AddressOfRecord(TO_USERNAME, SIP_URI, Map.of()));
		infoHeaders.setFrom(new AddressOfRecord(TO_USERNAME, SIP_URI, Map.of()));
		infoHeaders.setContactList(new ContactAny());
		infoHeaders.setCallId(CALL_ID);

		var overrideHeaders = new SipRequestHeaders();
		overrideHeaders.setContentType(JSON_MEDIA_TYPE);

		when(dialogService.makeDialogRequest(
				new DialogRequest(
						CALL_ID,
						INFO_METHOD,
						overrideHeaders,
						ICE_CANDIDATE.getBytes(StandardCharsets.UTF_8)
				)))
				.thenReturn(infoRequest);

		StepVerifier.create(requestHandler.handleMessage(sipRequest, context))
				.expectNext(ESTABLISH_DIALOG_RESPONSE)
				.expectNext(infoRequest)
				.expectComplete()
				.log()
				.verify();

		verify(reactiveConferenceSubscribersContext).notifyParticipantsChanged(CONFERENCE_ID);
	}

	@Test
	void getHandledMessageType() {
		assertThat(requestHandler.getHandledMessageType()).isEqualTo(INVITE);
	}
}