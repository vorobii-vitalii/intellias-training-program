package sip.reactor_netty.request_handling;

import java.io.ByteArrayInputStream;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import document_editor.netty_reactor.request_handling.ReactiveMessageHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import serialization.Deserializer;
import sip.SipMessage;
import sip.SipResponse;
import sip.reactor_netty.WSOutbound;
import sip.request_handling.invite.MediaConferenceService;

public class ConfirmParticipantOffersSipReactiveResponseHandler implements ReactiveMessageHandler<String, SipResponse, SipMessage, WSOutbound> {
	private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmParticipantOffersSipReactiveResponseHandler.class);
	private static final String NOTIFY = "NOTIFY";

	private final MediaConferenceService mediaConferenceService;
	private final Deserializer deserializer;

	public ConfirmParticipantOffersSipReactiveResponseHandler(MediaConferenceService mediaConferenceService, Deserializer deserializer) {
		this.mediaConferenceService = mediaConferenceService;
		this.deserializer = deserializer;
	}

	@Override
	public Flux<? extends SipMessage> handleMessage(SipResponse sipResponse, WSOutbound context) {
		var referenceURI = sipResponse.headers().getTo().toCanonicalForm().sipURI();
		LOGGER.info("Going to confirm participant offers, reference = {}", referenceURI);
		return Mono.fromCallable(() -> deserializer.deserialize(new ByteArrayInputStream(sipResponse.payload()), OnNotifyResponse.class))
				.flatMapMany(onNotifyResponse -> {
					LOGGER.info("On notify response = {}", onNotifyResponse);
					mediaConferenceService.processAnswers(getConferenceId(sipResponse), referenceURI,
							onNotifyResponse.sdpAnswerBySipURI());
					return Flux.empty();
				});
	}

	@Override
	public String getHandledMessageType() {
		return NOTIFY;
	}

	@Override
	public boolean canHandle(SipResponse sipResponse) {
		return sipResponse.payload().length > 0;
	}

	private String getConferenceId(SipResponse sipResponse) {
		var sipURI = sipResponse.headers().getFrom().sipURI();
		return sipURI.credentials().username();
	}

	private record OnNotifyResponse(Map<String, String> sdpAnswerBySipURI) {
	}

}
