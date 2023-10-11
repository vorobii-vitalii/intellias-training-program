package sip.request_handling;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import sip.CommandSequence;
import sip.ContactSet;
import sip.SipRequest;
import sip.SipRequestHeaders;
import sip.SipRequestLine;
import sip.SipResponse;
import sip.SipResponseLine;
import sip.SipStatusCode;
import sip.Via;

public class InMemoryInviteDialogService implements DialogService<SipSessionDescription> {
	private static final SipStatusCode SUCCESS_STATUS_CODE = new SipStatusCode(200);
	private static final String OK_REASON = "OK";
	private static final int MAX_FORWARDS = 70;
	private static final String TAG = "tag";

	private final Map<String, DialogContext> dialogsMap = new ConcurrentHashMap<>();

	@WithSpan
	@Override
	public SipResponse establishDialog(SipRequest sessionEstablishmentRequest, SipSessionDescription sipSessionDescription) {
		var callId = sessionEstablishmentRequest.headers().getCallId();
		dialogsMap.compute(callId, (s, dialogContext) -> {
			if (dialogContext != null) {
				throw new IllegalArgumentException("Dialog context by call id " + callId + " already exists");
			}
			return new DialogContext(
					sessionEstablishmentRequest,
					new AtomicInteger(sessionEstablishmentRequest.headers().getCommandSequence().sequenceNumber())
			);
		});
		var responseHeaders = sessionEstablishmentRequest.headers().toResponseHeaders();
		var tag = UUID.nameUUIDFromBytes(responseHeaders.getTo().toString().getBytes(StandardCharsets.UTF_8)).toString();
		responseHeaders.setTo(responseHeaders.getTo().addParam(TAG, tag));
		responseHeaders.setContentType(sipSessionDescription.sipMediaType());
		responseHeaders.setContactList(new ContactSet(Set.of(sessionEstablishmentRequest.headers().getTo())));
		return new SipResponse(
				new SipResponseLine(sessionEstablishmentRequest.requestLine().version(), SUCCESS_STATUS_CODE, OK_REASON),
				responseHeaders,
				sipSessionDescription.description().getBytes(StandardCharsets.UTF_8));
	}

	@WithSpan
	@Override
	public SipRequest makeDialogRequest(DialogRequest dialogRequest) {
		var dialogContext = getDialogContext(dialogRequest.callId());
		var request = dialogContext.sessionEstablishmentRequest();
		var sipRequestHeaders = new SipRequestHeaders();
		for (Via via : request.headers().getViaList()) {
			sipRequestHeaders.addVia(via);
		}
		var tag = UUID.nameUUIDFromBytes(request.headers().getTo().toString().getBytes(StandardCharsets.UTF_8)).toString();
		sipRequestHeaders.setFrom(request.headers().getTo().addParam(TAG, tag));
		sipRequestHeaders.setTo(request.headers().getFrom());
		sipRequestHeaders.setCommandSequence(new CommandSequence(dialogContext.sequenceNumber.incrementAndGet(), dialogRequest.methodName()));
		sipRequestHeaders.setCallId(request.headers().getCallId());
		sipRequestHeaders.setMaxForwards(MAX_FORWARDS);
		sipRequestHeaders.setContactList(new ContactSet(Set.of(request.headers().getTo())));
		return new SipRequest(
				new SipRequestLine(dialogRequest.methodName(), request.headers().getFrom().sipURI(), request.requestLine().version()),
				sipRequestHeaders.overrideWith(dialogRequest.overrideHeaders()),
				dialogRequest.body()
		);
	}

	@Nonnull
	private DialogContext getDialogContext(String callId) {
		var dialogContext = dialogsMap.get(callId);
		if (dialogContext == null) {
			throw new IllegalStateException("Dialog context by call id = " + callId + " not found");
		}
		return dialogContext;
	}

	private record DialogContext(SipRequest sessionEstablishmentRequest, AtomicInteger sequenceNumber) {
	}

}
