package sip.reactor_netty.request_handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import sip.Address;
import sip.AddressOfRecord;
import sip.ContactAny;
import sip.Credentials;
import sip.FullSipURI;
import sip.SipRequest;
import sip.SipRequestHeaders;
import sip.SipRequestLine;
import sip.SipVersion;
import sip.reactor_netty.WSOutbound;
import sip.reactor_netty.service.impl.ReactiveConferenceSubscribersContext;
import sip.request_handling.invite.ConferenceDisconnectRequest;
import sip.request_handling.invite.MediaConferenceService;

@ExtendWith(MockitoExtension.class)
class TestLeaveConferenceReactiveSipRequestHandler {
	private static final String BYE = "BYE";
	private static final String CONFERENCE_ID = "conference-id-12523523";
	private static final Credentials CREDENTIALS = new Credentials(CONFERENCE_ID, null);
	private static final String SIP = "sip";
	private static final FullSipURI SIP_URI = new FullSipURI(SIP, CREDENTIALS, new Address("host", null),
			Map.of(), Map.of());
	private static final SipVersion SIP_VERSION = new SipVersion(1, 1);
	private static final String DEFAULT_DISAMBIGUATOR = "";
	public static final String TO = "to";
	public static final String FROM = "from";

	@Mock
	MediaConferenceService mediaConferenceService;
	@Mock
	ReactiveConferenceSubscribersContext reactiveConferenceSubscribersContext;
	@InjectMocks
	LeaveConferenceReactiveSipRequestHandler requestHandler;

	@Mock
	WSOutbound wsOutbound;

	@Test
	void handleMessage() {
		var requestHeaders = new SipRequestHeaders();
		var sipRequest = new SipRequest(
				new SipRequestLine(BYE, SIP_URI, SIP_VERSION),
				requestHeaders,
				new byte[] {}
		);
		requestHeaders.setTo(new AddressOfRecord(TO, SIP_URI, Map.of()));
		requestHeaders.setFrom(new AddressOfRecord(FROM, SIP_URI, Map.of()));
		requestHeaders.setContactList(new ContactAny());

		when(mediaConferenceService.disconnectFromConference(new ConferenceDisconnectRequest(
				CONFERENCE_ID,
				SIP_URI.toCanonicalForm(),
				DEFAULT_DISAMBIGUATOR
		))).thenReturn(Mono.empty());

		StepVerifier.create(requestHandler.handleMessage(sipRequest, wsOutbound))
				.expectComplete()
				.log()
				.verify();
		verify(reactiveConferenceSubscribersContext).notifyParticipantsChanged(CONFERENCE_ID);
	}

	@Test
	void getHandledMessageType() {
		assertThat(requestHandler.getHandledMessageType()).isEqualTo(BYE);
	}
}