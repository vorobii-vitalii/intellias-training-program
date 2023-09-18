package sip.reactor_netty.service.impl;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import serialization.Serializer;
import sip.SipMediaType;
import sip.SipMessage;
import sip.SipRequest;
import sip.SipRequestHeaders;
import sip.request_handling.NewEventRequest;
import sip.request_handling.invite.MediaConferenceService;
import sip.request_handling.invite.ParticipantDTO;

public class ReactiveConferenceSubscribersContext {
	private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveConferenceSubscribersContext.class);
	private static final int BACKPRESSURE_BUFFER_SIZE = 1000;
	private static final String EVENT = "Event";
	private static final SipMediaType SIP_MEDIA_TYPE = SipMediaType.parse("application/json");
	private static final String CONFERENCE_EVENT_TYPE = "conference";
	private static final String NOTIFY_METHOD = "NOTIFY";

	private final ConferenceEventDialogService conferenceEventDialogService;
	private final MediaConferenceService mediaConferenceService;
	private final Serializer serializer;
	private final Map<String, Sinks.Many<ConferenceEvent>> conferenceSinkByConferenceId = new ConcurrentHashMap<>();

	public ReactiveConferenceSubscribersContext(
			ConferenceEventDialogService conferenceEventDialogService,
			MediaConferenceService mediaConferenceService,
			Serializer serializer
	) {
		this.conferenceEventDialogService = conferenceEventDialogService;
		this.mediaConferenceService = mediaConferenceService;
		this.serializer = serializer;
	}

	@WithSpan
	public void notifyParticipantsChanged(@SpanAttribute("conferenceId") String conferenceId) {
		// TODO: Check result of emit next
		createConferenceSinkIfNeeded(conferenceId).tryEmitNext(new ParticipantsChangedEvent());
	}

	@WithSpan
	public Flux<SipMessage> unsubscribeFromConferenceUpdates(SipRequest unsubscribeRequest) {
		var conferenceId = getConferenceId(unsubscribeRequest);
		var callId = unsubscribeRequest.headers().getCallId();
		var participantKey = calculateParticipantKey(unsubscribeRequest);
		LOGGER.info("Unsubscribing {} from conference {}", participantKey, conferenceId);
		createConferenceSinkIfNeeded(conferenceId)
				.tryEmitNext(new ParticipantDisconnectedEvent(participantKey));

		var unsubscribeEvent = conferenceEventDialogService.createNewEvent(new NewEventRequest(
				callId,
				NOTIFY_METHOD,
				new SipRequestHeaders(),
				new byte[] {},
				NewEventRequest.SubscriptionState.DESTROYED
		));
		conferenceEventDialogService.cancelSubscription(unsubscribeRequest);
		return Flux.just(unsubscribeEvent);
	}

	public Flux<SipMessage> subscribeToConferenceUpdates(SipRequest conferenceSubscribeRequest) {
		var subscriptionResponse = conferenceEventDialogService.createSubscription(conferenceSubscribeRequest);
		var conferenceId = getConferenceId(conferenceSubscribeRequest);
		var participantKey = calculateParticipantKey(conferenceSubscribeRequest);
		return Flux.<SipMessage>just(subscriptionResponse)
				.concatWith(getCurrentParticipantsMessage(conferenceSubscribeRequest))
				.concatWith(createConferenceSinkIfNeeded(conferenceId)
						.asFlux()
						.publishOn(Schedulers.boundedElastic())
						.takeUntil(event -> {
							if (event instanceof ParticipantDisconnectedEvent disconnect) {
								LOGGER.info("Received disconnect event in context of conference {}", conferenceId);
								if (disconnect.participantKey.equals(participantKey)) {
									LOGGER.info("Participant {} unsubscribed from Conference events... Wont send updates to him anymore...",
											participantKey);
									return true;
								}
							}
							return false;
						})
						.filter(event -> event instanceof ParticipantsChangedEvent)
						.doOnComplete(() -> {
							LOGGER.info("Completed stream subscribeToConferenceUpdates()");
						})
						.flatMap(conferenceEvent -> {
							LOGGER.info("Going to get participants in conference {} from perspective of {}", conferenceId, participantKey);
							return getCurrentParticipantsMessage(conferenceSubscribeRequest);
						})) ;
	}

	@WithSpan
	private Flux<SipMessage> getCurrentParticipantsMessage(SipRequest conferenceSubscribeRequest) {
		var subscriptionRequester = conferenceSubscribeRequest.headers().getFrom().toCanonicalForm().sipURI();
		LOGGER.info("Participants changed from perspective of {}", subscriptionRequester);
		var conferenceId = getConferenceId(conferenceSubscribeRequest);
		var callId = conferenceSubscribeRequest.headers().getCallId();
		return mediaConferenceService.getParticipantsFromPerspectiveOf(conferenceId, subscriptionRequester)
				.log()
				.flatMap(participant -> {
					LOGGER.info("Found participant = {}", participant.participantKey());
					return participant.iceCandidates()
							.buffer()
							.defaultIfEmpty(List.of())
							.map(candidates -> {
								LOGGER.info("All ICE candidates {} for candidate {} from  {} found!", candidates, participant.participantKey()
										, subscriptionRequester);
								return ParticipantDTO.create(
										participant.participantKey(), participant.sdpOffer(), candidates
								);
							});
				})
				.buffer()
				.defaultIfEmpty(List.of())
				.timed()
				.doOnComplete(() -> {
					LOGGER.info("Completed getting participants in getCurrentParticipantsMessage()...");
				})
				.handle((participants, sink) -> {
					LOGGER.info("ICE gathering process took {} ms", participants.elapsed().toMillis());
					try {
						var participantsSerialized = serializer.serialize(participants.get());
						var overrideHeaders = new SipRequestHeaders();
						overrideHeaders.setContentType(SIP_MEDIA_TYPE);
						overrideHeaders.addSingleHeader(EVENT, CONFERENCE_EVENT_TYPE);
						overrideHeaders.setContentLength(participantsSerialized.length);
						LOGGER.info("Participants serialized and sent {}",
								participants.get().stream().map(ParticipantDTO::participantKey).collect(Collectors.toList()));
						sink.next(conferenceEventDialogService.createNewEvent(new NewEventRequest(
								callId,
								NOTIFY_METHOD,
								overrideHeaders,
								participantsSerialized,
								NewEventRequest.SubscriptionState.ACTIVE
						)));
					}
					catch (IOException e) {
						sink.error(e);
					}
				});

	}

	private sealed interface ConferenceEvent permits ParticipantsChangedEvent, ParticipantDisconnectedEvent {
	}

	private record ParticipantsChangedEvent() implements ConferenceEvent {
	}

	private record ParticipantDisconnectedEvent(String participantKey) implements ConferenceEvent {
	}

	private Sinks.Many<ConferenceEvent> createConferenceSinkIfNeeded(String conferenceId) {
		return conferenceSinkByConferenceId.computeIfAbsent(conferenceId,
				s -> Sinks.many().multicast().onBackpressureBuffer(BACKPRESSURE_BUFFER_SIZE));
	}

	private String getConferenceId(SipRequest sipRequest) {
		var sipURI = sipRequest.requestLine().requestURI();
		return sipURI.credentials().username();
	}

	private String calculateParticipantKey(SipRequest unsubscribeRequest) {
		return unsubscribeRequest.headers().getFrom().toCanonicalForm().sipURI().getURIAsString();
	}

}
