package sip.request_handling.invite;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sip.AddressOfRecord;
import sip.ContactSet;
import sip.Credentials;
import sip.FullSipURI;
import sip.SipRequest;
import sip.SipResponse;
import sip.SipResponseLine;
import sip.SipStatusCode;
import sip.request_handling.SipRequestHandler;
import sip.request_handling.register.ConferencesStorage;
import tcp.MessageSerializer;
import tcp.server.OperationType;
import tcp.server.SocketConnection;

public class CreateConferenceRequestHandler implements SipRequestHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(CreateConferenceRequestHandler.class);

	private static final String INVITE = "INVITE";
	private static final int MOVED_PERMANENTLY = 301;
	private static final String REDIRECT_REASON = "Redirecting...";

	private final Predicate<AddressOfRecord> conferenceFactoryAddressOfRecord;
	private final Supplier<String> conferenceIdGenerator;
	private final MessageSerializer messageSerializer;
	private final MediaConferenceService mediaConferenceService;

	public CreateConferenceRequestHandler(
			Predicate<AddressOfRecord> conferenceFactoryAddressOfRecord,
			Supplier<String> conferenceIdGenerator,
			MessageSerializer messageSerializer,
			MediaConferenceService mediaConferenceService
	) {
		this.conferenceFactoryAddressOfRecord = conferenceFactoryAddressOfRecord;
		this.conferenceIdGenerator = conferenceIdGenerator;
		this.messageSerializer = messageSerializer;
		this.mediaConferenceService = mediaConferenceService;
	}

	@Override
	public void process(SipRequest sipRequest, SocketConnection socketConnection) {
		LOGGER.info("Creating new conference...");
		var conferenceId = conferenceIdGenerator.get();
		mediaConferenceService.createNewConference(conferenceId);
		sendCallRedirect(sipRequest, socketConnection, conferenceId);
	}

	private void sendCallRedirect(SipRequest sipRequest, SocketConnection socketConnection, String conferenceId) {
		var originalTo = sipRequest.headers().getTo();
		var sipResponseLine =
				new SipResponseLine(sipRequest.requestLine().version(), new SipStatusCode(MOVED_PERMANENTLY), REDIRECT_REASON);
		var sipResponseHeaders = sipRequest.headers().toResponseHeaders();
		var sipURI = originalTo.sipURI().updateCredentials(new Credentials(conferenceId, null));
		sipResponseHeaders.setContactList(new ContactSet(Set.of(
				new AddressOfRecord(originalTo.name(), sipURI, Map.of("ifocus", ""))
		)));
		// TODO: Take into consideration "expire" value, if not provided set reasonable default because conference cannot last forever
		var sipRedirectResponse = new SipResponse(sipResponseLine, sipResponseHeaders, new byte[] {});
		socketConnection.appendResponse(messageSerializer.serialize(sipRedirectResponse));
		socketConnection.changeOperation(OperationType.WRITE);
	}

	@Override
	public Set<String> getHandledTypes() {
		return Set.of(INVITE);
	}

	@Override
	public boolean canHandle(SipRequest sipRequest) {
		return conferenceFactoryAddressOfRecord.test(sipRequest.headers().getTo());
	}
}
