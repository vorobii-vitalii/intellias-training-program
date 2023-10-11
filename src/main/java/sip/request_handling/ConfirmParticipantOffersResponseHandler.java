package sip.request_handling;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import serialization.Deserializer;
import sip.FullSipURI;
import sip.SipResponse;
import sip.request_handling.invite.MediaConferenceService;
import tcp.server.SocketConnection;

public class ConfirmParticipantOffersResponseHandler implements SipMessageHandler<SipResponse>  {
	private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmParticipantOffersResponseHandler.class);
	private static final String NOTIFY = "NOTIFY";
	private final MediaConferenceService mediaConferenceService;
	private final Deserializer deserializer;

	public ConfirmParticipantOffersResponseHandler(MediaConferenceService mediaConferenceService, Deserializer deserializer) {
		this.mediaConferenceService = mediaConferenceService;
		this.deserializer = deserializer;
	}

	@Override
	public void process(SipResponse sipResponse, SocketConnection socketConnection) {
		try {
			LOGGER.info("Confirming participants offers");
			var onNotifyResponse = deserializer.deserialize(new ByteArrayInputStream(sipResponse.payload()), OnNotifyResponse.class);
			LOGGER.info("Response = {}", onNotifyResponse);
			var referenceURI = sipResponse.headers().getTo().toCanonicalForm().sipURI();
			mediaConferenceService.processAnswers(getConferenceId(sipResponse), referenceURI, onNotifyResponse.sdpAnswerBySipURI());
		}
		catch (IOException e) {
			LOGGER.error("COULD NOT PARSE {}", new String(sipResponse.payload()));
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean canHandle(SipResponse sipResponse) {
		return NOTIFY.equals(sipResponse.headers().getCommandSequence().commandName())
				&& mediaConferenceService.isConference(getConferenceId(sipResponse));
	}

	private String getConferenceId(SipResponse sipResponse) {
		var sipURI = sipResponse.headers().getFrom().sipURI();
		return sipURI.credentials().username();
	}

	private record OnNotifyResponse(Map<String, String> sdpAnswerBySipURI) {
	}
}
