package sip.reactor_netty.request_handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import reactor.test.StepVerifier;
import serialization.Deserializer;
import sip.Address;
import sip.AddressOfRecord;
import sip.Credentials;
import sip.FullSipURI;
import sip.SipResponse;
import sip.SipResponseHeaders;
import sip.SipResponseLine;
import sip.SipStatusCode;
import sip.SipVersion;
import sip.reactor_netty.WSOutbound;
import sip.reactor_netty.dto.OnNotifyResponse;
import sip.request_handling.invite.MediaConferenceService;

@ExtendWith(MockitoExtension.class)
class TestConfirmParticipantOffersSipReactiveResponseHandler {
	public static final String CONFERENCE_ID = "conferenceId";
	private static final Credentials CREDENTIALS = new Credentials(CONFERENCE_ID, null);
	private static final String SIP = "sip";
	private static final FullSipURI SIP_URI = new FullSipURI(SIP, CREDENTIALS, new Address("host", null),
			Map.of(), Map.of());
	private static final byte[] PAYLOAD = {1, 2, 3};
	private static final SipVersion SIP_VERSION = new SipVersion(1, 1);
	public static final String TO = "To";
	public static final AddressOfRecord TO_ADDRESS_OF_RECORD = new AddressOfRecord(TO, SIP_URI, Map.of());
	public static final String FROM = "FROM";
	public static final AddressOfRecord FROM_ADDRESS_OF_RECORD = new AddressOfRecord(FROM, SIP_URI, Map.of());
	public static final Map<String, String> SDP_ANSWER_BY_SIP_URI = Map.of(
			"p1", "sdp1",
			"p2", "sdp2"
	);
	private static final String NOTIFY = "NOTIFY";

	@Mock
	MediaConferenceService mediaConferenceService;
	@Mock
	Deserializer deserializer;

	@InjectMocks
	ConfirmParticipantOffersSipReactiveResponseHandler responseHandler;

	@Mock
	WSOutbound wsOutbound;

	@Test
	void handleMessage() throws IOException {
		var responseHeaders = new SipResponseHeaders();
		responseHeaders.setFrom(FROM_ADDRESS_OF_RECORD);
		responseHeaders.setTo(TO_ADDRESS_OF_RECORD);

		var sipResponse = new SipResponse(
				new SipResponseLine(SIP_VERSION, new SipStatusCode(200), "OK"),
				responseHeaders,
				PAYLOAD
		);

		when(deserializer.deserialize(any(InputStream.class), eq(OnNotifyResponse.class)))
				.thenReturn(new OnNotifyResponse(SDP_ANSWER_BY_SIP_URI));

		StepVerifier.create(responseHandler.handleMessage(sipResponse, wsOutbound))
				.expectComplete()
				.log()
				.verify();
		verify(mediaConferenceService).processAnswers(CONFERENCE_ID, TO_ADDRESS_OF_RECORD.sipURI().toCanonicalForm(), SDP_ANSWER_BY_SIP_URI);
	}

	@Test
	void getHandledMessageType() {
		assertThat(responseHandler.getHandledMessageType()).isEqualTo(NOTIFY);
	}
}