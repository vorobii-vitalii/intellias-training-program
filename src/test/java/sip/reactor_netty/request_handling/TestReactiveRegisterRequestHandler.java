package sip.reactor_netty.request_handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import sip.Address;
import sip.AddressOfRecord;
import sip.CommandSequence;
import sip.ContactAny;
import sip.ContactSet;
import sip.Credentials;
import sip.FullSipURI;
import sip.SipRequest;
import sip.SipRequestHeaders;
import sip.SipRequestLine;
import sip.SipStatusCode;
import sip.SipVersion;
import sip.reactor_netty.WSOutbound;
import sip.reactor_netty.service.ReactiveBindingStorage;
import sip.request_handling.register.CreateBinding;

@ExtendWith(MockitoExtension.class)
class TestReactiveRegisterRequestHandler {

	public static final Credentials CREDENTIALS = new Credentials(null, null);
	public static final String SIP = "sip";
	public static final FullSipURI SIP_URI = new FullSipURI(SIP, CREDENTIALS, new Address("host", null),
			Map.of(), Map.of());
	public static final String REGISTER = "REGISTER";
	public static final SipVersion SIP_VERSION = new SipVersion(1, 1);
	public static final String TO_USERNAME = "To";
	public static final int POSITIVE_EXPIRES = 123;
	public static final SipStatusCode BAD_REQUEST = new SipStatusCode(400);
	public static final int REMOVE_BINDINGS = 0;
	public static final String CALL_ID = "365363";
	public static final int SEQUENCE_NUMBER = 123;
	public static final int OK_STATUS = 200;

	@Mock
	ReactiveBindingStorage reactiveBindingStorage;

	@InjectMocks
	ReactiveRegisterRequestHandler reactiveRegisterRequestHandler;

	@Mock
	WSOutbound outbound;

	@Test
	void handleMessageGivenUnregisterRequestWithPositiveExpires() {
		var requestHeaders = new SipRequestHeaders();
		var sipRequest = new SipRequest(new SipRequestLine(REGISTER, SIP_URI, SIP_VERSION), requestHeaders, new byte[] {});
		requestHeaders.setTo(new AddressOfRecord(TO_USERNAME, SIP_URI, Map.of()));
		requestHeaders.setContactList(new ContactAny());
		requestHeaders.setExpires(POSITIVE_EXPIRES);
		StepVerifier.create(reactiveRegisterRequestHandler.handleMessage(sipRequest, outbound))
				.expectNextMatches(message -> message.responseLine().sipStatusCode().equals(BAD_REQUEST))
				.expectComplete()
				.log()
				.verify();
	}

	@Test
	void handleMessageGivenUnregisterRequestWithUnsetExpires() {
		var requestHeaders = new SipRequestHeaders();
		var sipRequest = new SipRequest(new SipRequestLine(REGISTER, SIP_URI, SIP_VERSION), requestHeaders, new byte[] {});
		requestHeaders.setTo(new AddressOfRecord(TO_USERNAME, SIP_URI, Map.of()));
		requestHeaders.setContactList(new ContactAny());
		StepVerifier.create(reactiveRegisterRequestHandler.handleMessage(sipRequest, outbound))
				.expectNextMatches(message -> message.responseLine().sipStatusCode().equals(BAD_REQUEST))
				.expectComplete()
				.log()
				.verify();
	}

	@Test
	void handleMessageGivenUnregisterRequestWithZeroExpires() {
		var requestHeaders = new SipRequestHeaders();
		var sipRequest = new SipRequest(new SipRequestLine(REGISTER, SIP_URI, SIP_VERSION), requestHeaders, new byte[] {});
		var to = new AddressOfRecord(TO_USERNAME, SIP_URI, Map.of());
		requestHeaders.setTo(to);
		requestHeaders.setContactList(new ContactAny());
		requestHeaders.setExpires(REMOVE_BINDINGS);
		when(reactiveBindingStorage.removeBindingsByAddressOfRecord(to.toCanonicalForm())).thenReturn(Mono.empty());
		StepVerifier.create(reactiveRegisterRequestHandler.handleMessage(sipRequest, outbound))
				.expectComplete()
				.log()
				.verify();
	}

	@Test
	void handleMessageGivenRegisterRequest() {
		var requestHeaders = new SipRequestHeaders();
		var sipRequest = new SipRequest(new SipRequestLine(REGISTER, SIP_URI, SIP_VERSION), requestHeaders, new byte[] {});
		var to = new AddressOfRecord(TO_USERNAME, SIP_URI, Map.of());
		requestHeaders.setTo(to);
		requestHeaders.setCallId(CALL_ID);
		requestHeaders.setContactList(new ContactSet(Set.of(to)));
		requestHeaders.setExpires(POSITIVE_EXPIRES);
		requestHeaders.setCommandSequence(new CommandSequence(SEQUENCE_NUMBER, REGISTER));
		when(reactiveBindingStorage.addBindings(outbound, to.toCanonicalForm(), List.of(new CreateBinding(to, CALL_ID, SEQUENCE_NUMBER)), POSITIVE_EXPIRES))
				.thenReturn(Mono.empty());
		when(reactiveBindingStorage.getAllBindingsByAddressOfRecord(to.toCanonicalForm()))
				.thenReturn(Flux.just(to));
		StepVerifier.create(reactiveRegisterRequestHandler.handleMessage(sipRequest, outbound))
				.expectNextMatches(sipResponse -> sipResponse.responseLine().sipStatusCode().statusCode() == OK_STATUS
						&& sipResponse.headers().getCallId().equals(CALL_ID))
				.expectComplete()
				.log()
				.verify();
	}

	@Test
	void getHandledMessageType() {
		assertThat(reactiveRegisterRequestHandler.getHandledMessageType()).isEqualTo(REGISTER);
	}
}
