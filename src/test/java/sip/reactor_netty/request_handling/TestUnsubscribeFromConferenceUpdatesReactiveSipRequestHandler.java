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
import sip.Address;
import sip.Credentials;
import sip.FullSipURI;
import sip.SipMessage;
import sip.SipRequest;
import sip.SipRequestHeaders;
import sip.SipRequestLine;
import sip.SipVersion;
import sip.reactor_netty.WSOutbound;
import sip.reactor_netty.service.impl.ReactiveConferenceSubscribersContext;

@ExtendWith(MockitoExtension.class)
class TestUnsubscribeFromConferenceUpdatesReactiveSipRequestHandler {
	private static final String SUBSCRIBE = "SUBSCRIBE";

	private static final SipRequest SUBSCRIBE_REQUEST = new SipRequest(new SipRequestLine("SUBSCRIBE", new FullSipURI(
			"sip",
			new Credentials(null, null),
			new Address("host", 2252),
			Map.of(),
			Map.of()
	), new SipVersion(1, 1)), new SipRequestHeaders().setExpires(22), new byte[] {});
	private static final SipRequest UNSUBSCRIBE_REQUEST = new SipRequest(new SipRequestLine("SUBSCRIBE", new FullSipURI(
			"sip",
			new Credentials(null, null),
			new Address("host", 2252),
			Map.of(),
			Map.of()
	), new SipVersion(1, 1)), new SipRequestHeaders().setExpires(0), new byte[] {});

	@Mock
	ReactiveConferenceSubscribersContext reactiveConferenceSubscribersContext;

	@InjectMocks
	UnsubscribeFromConferenceUpdatesReactiveSipRequestHandler requestHandler;

	@Mock
	WSOutbound context;

	@Mock
	SipMessage sipMessage;

	@Test
	void handleMessage() {
		var sipMessageFlux = Flux.just(sipMessage);
		when(reactiveConferenceSubscribersContext.unsubscribeFromConferenceUpdates(UNSUBSCRIBE_REQUEST))
				.thenReturn(sipMessageFlux);
		assertThat(requestHandler.handleMessage(UNSUBSCRIBE_REQUEST, context)).isEqualTo(sipMessageFlux);
	}

	@Test
	void getHandledMessageType() {
		assertThat(requestHandler.getHandledMessageType()).isEqualTo(SUBSCRIBE);
	}

	@Test
	void canHandleGivenExpiresZero() {
		assertThat(requestHandler.canHandle(UNSUBSCRIBE_REQUEST)).isTrue();
	}

	@Test
	void canHandleGivenExpiresPositive() {
		assertThat(requestHandler.canHandle(SUBSCRIBE_REQUEST)).isFalse();
	}

}
