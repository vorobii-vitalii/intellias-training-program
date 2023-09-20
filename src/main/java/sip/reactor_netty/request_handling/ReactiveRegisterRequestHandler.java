package sip.reactor_netty.request_handling;

import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.annotation.Counted;
import request_handler.ReactiveMessageHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sip.AddressOfRecord;
import sip.ContactAny;
import sip.ContactSet;
import sip.SipMessage;
import sip.SipRequest;
import sip.SipResponse;
import sip.SipResponseHeaders;
import sip.SipResponseLine;
import sip.SipStatusCode;
import sip.Via;
import sip.reactor_netty.WSOutbound;
import sip.reactor_netty.service.ReactiveBindingStorage;
import sip.request_handling.register.CreateBinding;

public class ReactiveRegisterRequestHandler implements ReactiveMessageHandler<String, SipRequest, SipMessage, WSOutbound> {
	public static final SipStatusCode BAD_REQUEST = new SipStatusCode(400);
	private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveRegisterRequestHandler.class);
	private static final Integer REMOVE_BINDINGS = 0;
	// TODO: Create parameter instead
	private static final int DEFAULT_EXPIRATION = 100_000;

	private final ReactiveBindingStorage reactiveBindingStorage;

	public ReactiveRegisterRequestHandler(ReactiveBindingStorage reactiveBindingStorage) {
		this.reactiveBindingStorage = reactiveBindingStorage;
	}

	@Counted(value = "register.count")
	@Override
	public Flux<SipResponse> handleMessage(SipRequest sipRequest, WSOutbound outbound) {
		return Mono.just(sipRequest).flatMapMany(request -> {
			var addressOfRecord = request.headers().getTo().toCanonicalForm();
			var contactList = request.headers().getContactList();
			if (contactList == null) {
				return Flux.empty();
			}
			// Unintuitive but REGISTER request with Contact = * means unregister...
			if (contactList instanceof ContactAny) {
				var expiresValue = request.headers().getExpires();
				if (!REMOVE_BINDINGS.equals(expiresValue)) {
					LOGGER.error("""
							If the request has additional Contact
							         fields or an expiration time other than zero, the request is
							         invalid, and the server MUST return a 400 (Invalid Request)""");
					return Flux.just(createBadRequestResponse(request));
				}
				return reactiveBindingStorage.removeBindingsByAddressOfRecord(addressOfRecord)
						.thenMany(Flux.empty());
			} else {
				var newBindings = ((ContactSet) contactList).allowedAddressOfRecords()
						.stream()
						.map(r -> new CreateBinding(r, request.headers().getCallId(),
								request.headers().getCommandSequence().sequenceNumber()))
						.collect(Collectors.toList());
				var expiration = Optional.ofNullable(request.headers().getExpires()).orElse(DEFAULT_EXPIRATION);
				return reactiveBindingStorage.addBindings(outbound, addressOfRecord, newBindings, expiration)
						.thenMany(createOKResponse(request, addressOfRecord));
			}
		});
	}

	@Override
	public String getHandledMessageType() {
		return "REGISTER";
	}

	private Flux<SipResponse> createOKResponse(SipRequest sipRequest, AddressOfRecord addressOfRecord) {
		return reactiveBindingStorage.getAllBindingsByAddressOfRecord(addressOfRecord)
				.buffer()
				.map(addressOfRecords -> {
					var sipResponseHeaders = new SipResponseHeaders();
					for (Via via : sipRequest.headers().getViaList()) {
						sipResponseHeaders.addVia(via.normalize());
					}
					sipResponseHeaders.setFrom(sipRequest.headers().getFrom());
					sipResponseHeaders.setTo(sipRequest.headers().getTo());
					sipResponseHeaders.setContactList(new ContactSet(addressOfRecords));
					sipResponseHeaders.setCallId(sipRequest.headers().getCallId());
					sipResponseHeaders.setCommandSequence(sipRequest.headers().getCommandSequence());
					return new SipResponse(
							new SipResponseLine(
									sipRequest.requestLine().version(),
									new SipStatusCode(200),
									"OK"
							),
							sipResponseHeaders,
							new byte[] {}
					);
				});
	}

	private SipResponse createBadRequestResponse(SipRequest sipRequest) {
		return new SipResponse(
				new SipResponseLine(sipRequest.requestLine().version(), BAD_REQUEST, "Invalid request"),
				new SipResponseHeaders(),
				new byte[] {}
		);
	}

}
