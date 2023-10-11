package sip.reactor_netty.request_handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
import sip.ContactSet;
import sip.Credentials;
import sip.FullSipURI;
import sip.SipRequest;
import sip.SipRequestHeaders;
import sip.SipRequestLine;
import sip.SipVersion;
import sip.reactor_netty.WSOutbound;
import sip.request_handling.invite.MediaConferenceService;

@ExtendWith(MockitoExtension.class)
class TestCreateConferenceReactiveSipRequestHandler {
	public static final Credentials CREDENTIALS = new Credentials(null, null);
	public static final String SIP = "sip";
	public static final FullSipURI SIP_URI = new FullSipURI(SIP, CREDENTIALS, new Address("host", null),
			Map.of(), Map.of());
	private static final int MOVED_PERMANENTLY = 301;
	private static final String CONFERENCE_ID = "235362346";
	public static final String INVITE = "INVITE";
	public static final SipVersion SIP_VERSION = new SipVersion(1, 1);
	public static final String TO_USERNAME = "To";

	@Mock
	Predicate<AddressOfRecord> conferenceFactoryAddressOfRecord;
	@Mock
	Supplier<String> conferenceIdGenerator;
	@Mock
	MediaConferenceService mediaConferenceService;

	@InjectMocks
	CreateConferenceReactiveSipRequestHandler requestHandler;

	@Mock
	WSOutbound wsOutbound;

	@Test
	void handleMessage() {
		var requestHeaders = new SipRequestHeaders();
		var sipRequest = new SipRequest(new SipRequestLine(INVITE, SIP_URI, SIP_VERSION), requestHeaders, new byte[] {});
		requestHeaders.setTo(new AddressOfRecord(TO_USERNAME, SIP_URI, Map.of()));
		requestHeaders.setContactList(new ContactAny());

		when(conferenceIdGenerator.get()).thenReturn(CONFERENCE_ID);
		when(mediaConferenceService.createNewConferenceReactive(CONFERENCE_ID)).thenReturn(Mono.empty());

		StepVerifier.create(requestHandler.handleMessage(sipRequest, wsOutbound))
				.expectNextMatches(message -> {
					var addressOfRecord = ((ContactSet) message.headers().getContactList())
							.allowedAddressOfRecords()
							.stream()
							.findFirst()
							.orElseThrow();
					if (!addressOfRecord.sipURI().credentials().username().equals(CONFERENCE_ID)) {
						return false;
					}
					return message.responseLine().sipStatusCode().statusCode() == MOVED_PERMANENTLY;
				})
				.expectComplete()
				.log()
				.verify();
	}

	@Test
	void getHandledMessageType() {
		assertThat(requestHandler.getHandledMessageType()).isEqualTo(INVITE);
	}
}