package sip.reactor_netty.request_handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
import sip.SipRequest;
import sip.SipRequestHeaders;
import sip.SipRequestLine;
import sip.SipVersion;
import sip.reactor_netty.WSOutbound;
import sip.reactor_netty.service.impl.ReactiveConferenceSubscribersContext;

@ExtendWith(MockitoExtension.class)
class TestSubscribeToConferenceUpdatesReactiveSipRequestHandler {
	private static final Credentials CREDENTIALS = new Credentials(null, null);
	private static final String SIP = "sip";
	private static final FullSipURI SIP_URI = new FullSipURI(SIP, CREDENTIALS, new Address("host", null),
			Map.of(), Map.of());
	private static final SipVersion SIP_VERSION = new SipVersion(1, 1);
	private static final String TO_USERNAME = "To";
	private static final int POSITIVE_EXPIRES = 123;
	private static final String SUBSCRIBE = "SUBSCRIBE";
	private static final int ZERO = 0;

	@Mock
	ReactiveConferenceSubscribersContext reactiveConferenceSubscribersContext;

	@InjectMocks
	SubscribeToConferenceUpdatesReactiveSipRequestHandler requestHandler;

	@Mock
	WSOutbound outbound;

	@Test
	void handleMessage() {
		var requestHeaders = new SipRequestHeaders();
		var sipRequest = new SipRequest(new SipRequestLine(SUBSCRIBE, SIP_URI, SIP_VERSION), requestHeaders, new byte[] {});
		requestHeaders.setTo(new AddressOfRecord(TO_USERNAME, SIP_URI, Map.of()));
		requestHeaders.setContactList(new ContactAny());
		requestHeaders.setExpires(POSITIVE_EXPIRES);
		when(reactiveConferenceSubscribersContext.subscribeToConferenceUpdates(sipRequest))
				.thenReturn(Flux.empty());
		StepVerifier.create(requestHandler.handleMessage(sipRequest, outbound))
				.expectComplete()
				.log()
				.verify();
	}

	@Test
	void getHandledMessageType() {
		assertThat(requestHandler.getHandledMessageType()).isEqualTo(SUBSCRIBE);
	}

	@Test
	void canHandleGivenExpireZero() {
		var requestHeaders = new SipRequestHeaders();
		var sipRequest = new SipRequest(new SipRequestLine(SUBSCRIBE, SIP_URI, SIP_VERSION), requestHeaders, new byte[] {});
		requestHeaders.setTo(new AddressOfRecord(TO_USERNAME, SIP_URI, Map.of()));
		requestHeaders.setContactList(new ContactAny());
		requestHeaders.setExpires(ZERO);
		assertThat(requestHandler.canHandle(sipRequest)).isFalse();
	}

	@Test
	void canHandleGivenExpirePositive() {
		var requestHeaders = new SipRequestHeaders();
		var sipRequest = new SipRequest(new SipRequestLine(SUBSCRIBE, SIP_URI, SIP_VERSION), requestHeaders, new byte[] {});
		requestHeaders.setTo(new AddressOfRecord(TO_USERNAME, SIP_URI, Map.of()));
		requestHeaders.setContactList(new ContactAny());
		requestHeaders.setExpires(POSITIVE_EXPIRES);
		assertThat(requestHandler.canHandle(sipRequest)).isTrue();
	}

}
