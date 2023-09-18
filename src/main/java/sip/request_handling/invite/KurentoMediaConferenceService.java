package sip.request_handling.invite;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nonnull;

import org.kurento.client.IceCandidatePair;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaType;
import org.kurento.client.OfferOptions;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sip.FullSipURI;

// Screensharing, only view + 1
// Add Jaeger
// Change approach for ICE gathering + 1
// Reactive streams + 1
public class KurentoMediaConferenceService implements MediaConferenceService {
	private static final Logger LOGGER = LoggerFactory.getLogger(KurentoMediaConferenceService.class);
	private final Map<String, Conference> mediaPipelineByConferenceId = new ConcurrentHashMap<>();

	private final KurentoClient kurentoClient;

	public KurentoMediaConferenceService(KurentoClient kurentoClient) {
		this.kurentoClient = kurentoClient;
	}

	private static OfferOptions createReceiveOnlyOfferOptions() {
		var offerOptions = new OfferOptions();
		offerOptions.setOfferToReceiveAudio(true);
		offerOptions.setOfferToReceiveVideo(true);
		return offerOptions;
	}

	@WithSpan
	@Override
	public Mono<Void> createNewConferenceReactive(@SpanAttribute("conferenceId") String conferenceId) {
		return Mono.create(sink -> {
			LOGGER.info("Creating new media pipeline for new conference {}", conferenceId);
			var mediaPipeline = kurentoClient.createMediaPipeline();
			// TODO: Call this function when conference ends
			// mediaPipeline.release();
			mediaPipelineByConferenceId.put(conferenceId, new Conference(mediaPipeline, new ConcurrentHashMap<>(), new ReentrantLock(true)));
			sink.success();
		});
	}

	@WithSpan
	@Override
	public void createNewConference(@SpanAttribute("conferenceId") String conferenceId) {
		var mediaPipeline = kurentoClient.createMediaPipeline();
		// TODO: Call this function when conference ends
		// mediaPipeline.release();
		mediaPipelineByConferenceId.put(conferenceId, new Conference(mediaPipeline, new ConcurrentHashMap<>(), new ReentrantLock(true)));
	}

	@WithSpan
	@Override
	public ConferenceJoinResponse connectToConferenceReactive(ConferenceJoinRequest conferenceJoinRequest) {
		var conference = getConference(conferenceJoinRequest.conferenceId());
		var webRtcEndpoint = create(conference.mediaPipeline);
		var participantKey = calculateParticipantKey(conferenceJoinRequest);
		var sdpResponse = webRtcEndpoint.processOffer(conferenceJoinRequest.sdpOffer());
		var isReceiving = conferenceJoinRequest.mode().receive();
		var isSending = conferenceJoinRequest.mode().send();
		var receiveConnectionBySipURI = new ConcurrentHashMap<String, ReceiveConnection>();
		try {
			conference.consistencyLock().lock();
			for (var entry : conference.webRtcEndpointMap().entrySet()) {
				var sendEndpoint = entry.getValue().sendRTCEndpoint();
				if (isSending) {
					var receiveLeftEndpoint = createIntermediateEndpoint(webRtcEndpoint, conference.mediaPipeline);
					var sdpOffer = receiveLeftEndpoint.generateOffer(createReceiveOnlyOfferOptions());
					entry.getValue().receiveConnectionByParticipantKey.put(
							participantKey,
							new ReceiveConnection(receiveLeftEndpoint, sdpOffer, gatherCandidatesReactive(receiveLeftEndpoint,
									participantKey + " -> " + entry.getKey())));
				}
				if (isReceiving) {
					var receiveRightEndpoint = createIntermediateEndpoint(sendEndpoint, conference.mediaPipeline);
					var sdpOffer = receiveRightEndpoint.generateOffer(createReceiveOnlyOfferOptions());
					receiveConnectionBySipURI.put(
							entry.getKey(),
							new ReceiveConnection(receiveRightEndpoint, sdpOffer, gatherCandidatesReactive(receiveRightEndpoint,
									entry.getKey() + " -> " + participantKey)));
				}
			}
			conference.webRtcEndpointMap().put(participantKey, new UserMediaContext(webRtcEndpoint, receiveConnectionBySipURI));
		}
		finally {
			conference.consistencyLock.unlock();
		}
		return new ConferenceJoinResponse(sdpResponse, gatherCandidatesReactive(webRtcEndpoint, "client -> " + participantKey));
	}

	@WithSpan
	@Override
	public Mono<Void> disconnectFromConference(ConferenceDisconnectRequest conferenceDisconnectRequest) {
		return Mono.fromCallable(() -> {
			var conference = getConference(conferenceDisconnectRequest.conferenceId());
			try {
				conference.consistencyLock().lock();
				var participantKey = calculateParticipantKey(conferenceDisconnectRequest);
				for (var entry : conference.webRtcEndpointMap().entrySet()) {
					if (entry.getKey().equals(participantKey)) {
						continue;
					}
					entry.getValue().receiveConnectionByParticipantKey().remove(participantKey);
				}
				conference.webRtcEndpointMap().remove(participantKey);
			}
			finally {
				conference.consistencyLock().unlock();
			}
			return null;
		});
	}

	private String calculateParticipantKey(ConferenceDisconnectRequest conferenceDisconnectRequest) {
		return conferenceDisconnectRequest.sipURI().getURIAsString() + conferenceDisconnectRequest.disambiguator();
	}

	private String calculateParticipantKey(ConferenceJoinRequest conferenceJoinRequest) {
		return conferenceJoinRequest.sipURI().getURIAsString() + conferenceJoinRequest.disambiguator();
	}

	private Flux<String> gatherCandidatesReactive(WebRtcEndpoint webRtcEndpoint, String endpointName) {
		return Flux.<String> create(sink -> {
			LOGGER.info("Gathering ICE candidates... endpoint = {}", endpointName);
			webRtcEndpoint.addIceCandidateFoundListener(event -> {
				LOGGER.info("Ice candidate found: endpoint = {} candidate {}", endpointName, event.getCandidate());
				sink.next(event.getCandidate().getCandidate());
			});
			webRtcEndpoint.addIceGatheringDoneListener(event -> {
				LOGGER.info("Gathering completed endpoint = {}", endpointName);
				sink.complete();
			});
			webRtcEndpoint.gatherCandidates();
		}).cache();
	}

	private WebRtcEndpoint create(MediaPipeline mediaPipeline) {
		return new WebRtcEndpoint.Builder(mediaPipeline).build();
	}

	private WebRtcEndpoint createIntermediateEndpoint(WebRtcEndpoint source, MediaPipeline mediaPipeline) {
		var intermediateEndpoint = createEndpointInConference(mediaPipeline);
		source.connect(intermediateEndpoint, MediaType.AUDIO);
		source.connect(intermediateEndpoint, MediaType.VIDEO);
		return intermediateEndpoint;
	}

	private WebRtcEndpoint createEndpointInConference(MediaPipeline mediaPipeline) {
		//		webRtcEndpoint.setStunServerAddress("stun.l.google.com");
//		webRtcEndpoint.setStunServerPort(19302);
//		webRtcEndpoint.gatherCandidates();
		return new WebRtcEndpoint.Builder(mediaPipeline).build();
	}

	@Override
	public boolean isConference(String conferenceId) {
		return mediaPipelineByConferenceId.containsKey(conferenceId);
	}

	@WithSpan
	@Override
	public Flux<Participant> getParticipantsFromPerspectiveOf(
			@SpanAttribute("conferenceId") String conferenceId,
			@SpanAttribute("sipURI") FullSipURI referenceURI
	) {
		var conference = mediaPipelineByConferenceId.get(conferenceId);
		if (conference == null) {
			return Flux.empty();
		}
		UserMediaContext userMediaContext = conference.webRtcEndpointMap()
				.get(referenceURI.getURIAsString());
		if (userMediaContext == null) {
			return Flux.empty();
		}
		LOGGER.info("Get participants from perspective {}", referenceURI);
		return Flux.fromStream(() -> {
			LOGGER.info("Participants are now requested");
			return userMediaContext
					.receiveConnectionByParticipantKey()
					.entrySet()
					.stream()
					.map(e -> {
						var sdpOffer = e.getValue().sdpOffer();
						var participantKey = e.getKey();
						return new Participant(participantKey, sdpOffer, e.getValue().iceCandidates());
					});
		});
	}

	@WithSpan
	@Override
	public Mono<Void> processAnswersReactive(String conferenceId, FullSipURI referenceURI, Map<String, String> sdpAnswerByParticipantKey) {
		return Mono.fromCallable(() -> {
			LOGGER.info("Processing answer reactive...");
			var conference = getConference(conferenceId);
			var userMediaContext = conference.webRtcEndpointMap().get(referenceURI.getURIAsString());
			for (var entry : sdpAnswerByParticipantKey.entrySet()) {
				var receiveConnection = userMediaContext.receiveConnectionByParticipantKey().get(entry.getKey());
				if (receiveConnection == null) {
					continue;
				}
//				LOGGER.info("Processing answer for {}", entry.getKey());
				receiveConnection.endpoint().processAnswer(entry.getValue());
//				LOGGER.info("Process answer result = {}", processAnswer);
			}
			return null;
		});
	}

	@WithSpan
	@Override
	public void processAnswers(String conferenceId, FullSipURI referenceURI, Map<String, String> sdpAnswerByParticipantKey) {
		var conference = getConference(conferenceId);
		var userMediaContext = conference.webRtcEndpointMap().get(referenceURI.getURIAsString());
		for (var entry : sdpAnswerByParticipantKey.entrySet()) {
			final ReceiveConnection receiveConnection = userMediaContext.receiveConnectionByParticipantKey().get(entry.getKey());
			if (receiveConnection == null) {
				continue;
			}
			receiveConnection.endpoint().processAnswer(entry.getValue());
		}
	}

	@Nonnull
	private Conference getConference(String conferenceId) {
		var conference = mediaPipelineByConferenceId.get(conferenceId);
		if (conference == null) {
			throw new IllegalArgumentException("Conference " + conferenceId + " doesn't exist!");
		}
		return conference;
	}

	private record Conference(MediaPipeline mediaPipeline, Map<String, UserMediaContext> webRtcEndpointMap, Lock consistencyLock) {
	}

	private record ReceiveConnection(WebRtcEndpoint endpoint, String sdpOffer, Flux<String> iceCandidates) {
	}

	private record UserMediaContext(WebRtcEndpoint sendRTCEndpoint, Map<String, ReceiveConnection> receiveConnectionByParticipantKey) {
	}

}
