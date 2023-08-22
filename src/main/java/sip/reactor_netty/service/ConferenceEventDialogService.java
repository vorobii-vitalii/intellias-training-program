package sip.reactor_netty.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import sip.CommandSequence;
import sip.ContactSet;
import sip.SipRequest;
import sip.SipRequestHeaders;
import sip.SipRequestLine;
import sip.SipResponse;
import sip.SipResponseHeaders;
import sip.SipResponseLine;
import sip.SipStatusCode;
import sip.SipVersion;
import sip.Via;
import sip.request_handling.DialogRequest;

public class ConferenceEventDialogService {
	private static final String EXPIRES = "Expires";
	private static final String TAG = "tag";
	private static final int MAX_FORWARDS = 70;
	public static final SipVersion SIP_VERSION = new SipVersion(2, 0);
	public static final SipStatusCode SIP_STATUS_CODE = new SipStatusCode(202);
	public static final String OK_REASON_PHRASE = "OK";

	private final Map<String, SubscriptionContext> subscriptionsMap = new ConcurrentHashMap<>();

	public SipResponse createSubscription(SipRequest subscribeMessage) {
		var callId = subscribeMessage.headers().getCallId();
		var expires = subscribeMessage.headers().getExpires();
		subscriptionsMap.compute(callId, (s, context) -> {
			return new SubscriptionContext(
					subscribeMessage,
					getCurrentTimestamp().plusSeconds(expires),
					new AtomicInteger(subscribeMessage.headers().getCommandSequence().sequenceNumber())
			);
		});

		var responseLine = new SipResponseLine(SIP_VERSION, SIP_STATUS_CODE, OK_REASON_PHRASE);
		var responseHeaders = new SipResponseHeaders();
		for (Via via : subscribeMessage.headers().getViaList()) {
			responseHeaders.addVia(via);
		}
		responseHeaders.setFrom(subscribeMessage.headers().getFrom());
//		var tag = UUID.nameUUIDFromBytes(responseHeaders.getTo().toString().getBytes(StandardCharsets.UTF_8)).toString();
		responseHeaders.setTo(subscribeMessage.headers().getTo());
		responseHeaders.setCallId(subscribeMessage.headers().getCallId());
		responseHeaders.setCommandSequence(subscribeMessage.headers().getCommandSequence());
		// TODO: Schedule task to unsubscribe the client after expires ms...
		responseHeaders.addHeader(EXPIRES, String.valueOf(expires));
		responseHeaders.setContactList(subscribeMessage.headers().getContactList());
		return new SipResponse(responseLine, responseHeaders, new byte[] {});
	}

	public SipRequest makeDialogRequest(DialogRequest dialogRequest) {
		var subscriptionContext = getContext(dialogRequest.callId());
		var request = subscriptionContext.initialRequest();
		var sipRequestHeaders = new SipRequestHeaders();
		for (Via via : request.headers().getViaList()) {
			sipRequestHeaders.addVia(via);
		}
		var tag = UUID.nameUUIDFromBytes(request.headers().getTo().toString().getBytes(StandardCharsets.UTF_8)).toString();
		sipRequestHeaders.setFrom(request.headers().getTo().addParam(TAG, tag));
		sipRequestHeaders.setTo(request.headers().getFrom());
		sipRequestHeaders.setCommandSequence(new CommandSequence(subscriptionContext.sequenceNumber().incrementAndGet(), dialogRequest.methodName()));
		sipRequestHeaders.setCallId(request.headers().getCallId());
		sipRequestHeaders.setMaxForwards(MAX_FORWARDS);
		sipRequestHeaders.setContactList(new ContactSet(Set.of(request.headers().getTo())));
		return new SipRequest(
				new SipRequestLine(dialogRequest.methodName(), request.headers().getFrom().sipURI(), request.requestLine().version()),
				sipRequestHeaders.overrideWith(dialogRequest.overrideHeaders()),
				dialogRequest.body()
		);
	}

	private Instant getCurrentTimestamp() {
		return Instant.now();
	}

	@Nonnull
	private SubscriptionContext getContext(String callId) {
		var dialogContext = subscriptionsMap.get(callId);
		if (dialogContext == null) {
			throw new IllegalStateException("Context by call id = " + callId + " not found");
		}
		return dialogContext;
	}

	private record SubscriptionContext(SipRequest initialRequest, Instant subscriptionEnd, AtomicInteger sequenceNumber) {
	}

}
