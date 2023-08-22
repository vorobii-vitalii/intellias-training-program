package sip.reactor_netty.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import serialization.Serializer;
import sip.FullSipURI;
import sip.SipMediaType;
import sip.SipMessage;
import sip.SipRequest;
import sip.SipRequestHeaders;
import sip.request_handling.DialogRequest;
import sip.request_handling.invite.MediaConferenceService;
import sip.request_handling.invite.ParticipantDTO;

public class ReactiveConferenceSubscribersContext {
	private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveConferenceSubscribersContext.class);
	private static final int BACKPRESSURE_BUFFER_SIZE = 1000;
	private static final String EVENT = "Event";
	private static final String SUBSCRIPTION_STATE = "Subscription-State";
	private static final SipMediaType SIP_MEDIA_TYPE = SipMediaType.parse("application/json");
	private static final String CONFERENCE_EVENT_TYPE = "conference";
	private static final String NOTIFY_METHOD = "NOTIFY";

	private final ConferenceEventDialogService conferenceEventDialogService;
	private final MediaConferenceService mediaConferenceService;
	private final Serializer serializer;
	private final Map<String, Sinks.Many<ParticipantsChanged>> conferenceSinkByConferenceId = new ConcurrentHashMap<>();

	public ReactiveConferenceSubscribersContext(
			ConferenceEventDialogService conferenceEventDialogService,
			MediaConferenceService mediaConferenceService,
			Serializer serializer
	) {
		this.conferenceEventDialogService = conferenceEventDialogService;
		this.mediaConferenceService = mediaConferenceService;
		this.serializer = serializer;
	}

	public void notifyParticipantsChanged(String conferenceId) {
		// TODO: Check result of emit next
		createConferenceSinkIfNeeded(conferenceId).tryEmitNext(new ParticipantsChanged());
	}

	public Flux<SipMessage> subscribeToConferenceUpdates(SipRequest conferenceSubscribeRequest) {
		var subscriptionResponse = conferenceEventDialogService.createSubscription(conferenceSubscribeRequest);
		var conferenceId = getConferenceId(conferenceSubscribeRequest);
		// TODO: Handle BYE
		return Flux.<SipMessage>just(subscriptionResponse)
				.concatWith(getCurrentParticipantsMessage(conferenceSubscribeRequest))
				.concatWith(createConferenceSinkIfNeeded(conferenceId)
						.asFlux()
						.flatMap(conferenceEvent -> getCurrentParticipantsMessage(conferenceSubscribeRequest))) ;
	}

	private Flux<SipMessage> getCurrentParticipantsMessage(SipRequest conferenceSubscribeRequest) {
		var subscriptionRequester = conferenceSubscribeRequest.headers().getFrom().toCanonicalForm().sipURI();
		LOGGER.info("Participants changed from perspective of {}", subscriptionRequester);
		var conferenceId = getConferenceId(conferenceSubscribeRequest);
		var callId = conferenceSubscribeRequest.headers().getCallId();
		return mediaConferenceService.getParticipantsFromPerspectiveOf(conferenceId, subscriptionRequester)
				.log()
				.flatMap(participant -> {
					LOGGER.info("Found participant = {}", participant);
					return participant.iceCandidates()
							.buffer()
							.map(candidates -> ParticipantDTO.create(
									participant.participantKey(), participant.sdpOffer(), candidates
							));
				})
				.buffer()
				.defaultIfEmpty(List.of())
				.handle((participants, sink) -> {
					try {
						var participantsSerialized = serializer.serialize(participants);
						var overrideHeaders = new SipRequestHeaders();
						overrideHeaders.setContentType(SIP_MEDIA_TYPE);
						overrideHeaders.addSingleHeader(EVENT, CONFERENCE_EVENT_TYPE);
						overrideHeaders.addSingleHeader(SUBSCRIPTION_STATE, "active;expires=240");
						overrideHeaders.setContentLength(participantsSerialized.length);
						LOGGER.info("Participants serialized and sent...");
						sink.next(conferenceEventDialogService.makeDialogRequest(new DialogRequest(
								callId,
								NOTIFY_METHOD,
								overrideHeaders,
								participantsSerialized
						)));
					}
					catch (IOException e) {
						sink.error(e);
					}
				});

	}

	private record ParticipantsChanged() {
	}

	private Sinks.Many<ParticipantsChanged> createConferenceSinkIfNeeded(String conferenceId) {
		return conferenceSinkByConferenceId.computeIfAbsent(conferenceId,
				s -> Sinks.many().multicast().onBackpressureBuffer(BACKPRESSURE_BUFFER_SIZE));
	}

	private String getConferenceId(SipRequest sipRequest) {
		var sipURI = (FullSipURI) sipRequest.requestLine().requestURI();
		return sipURI.credentials().username();
	}

}
