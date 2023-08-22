package sip.request_handling.invite;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.kurento.client.Continuation;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaType;
import org.kurento.client.OfferOptions;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sip.SipURI;

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

	@Override
	public Mono<Void> createNewConferenceReactive(String conferenceId) {
		return Mono.create(sink -> {
			LOGGER.info("Creating new media pipeline for new conference {}", conferenceId);
			var mediaPipeline = kurentoClient.createMediaPipeline();
			// TODO: Call this function when conference ends
			// mediaPipeline.release();
			mediaPipelineByConferenceId.put(conferenceId, new Conference(mediaPipeline, new ConcurrentHashMap<>()));
			sink.success();
		});
	}

	@Override
	public void createNewConference(String conferenceId) {
		var mediaPipeline = kurentoClient.createMediaPipeline();
		// TODO: Call this function when conference ends
		// mediaPipeline.release();
		mediaPipelineByConferenceId.put(conferenceId, new Conference(mediaPipeline, new ConcurrentHashMap<>()));
	}

	@Override
	public ConferenceJoinResponse connectToConferenceReactive(ConferenceJoinRequest conferenceJoinRequest) {
		var conference = getConference(conferenceJoinRequest.conferenceId());
		var webRtcEndpoint = create(conference.mediaPipeline);
		var participantKey = calculateParticipantKey(conferenceJoinRequest);
//		webRtcEndpoint.gatherCandidates();
		var sdpResponse = webRtcEndpoint.processOffer(conferenceJoinRequest.sdpOffer());
		var isReceiving = conferenceJoinRequest.mode().receive();
		var isSending = conferenceJoinRequest.mode().send();
		var receiveConnectionBySipURI = new ConcurrentHashMap<String, ReceiveConnection>();
		for (var entry : conference.webRtcEndpointMap().entrySet()) {
			var sendEndpoint = entry.getValue().sendRTCEndpoint();
			if (isSending) {
				var receiveLeftEndpoint = createIntermediateEndpoint(webRtcEndpoint, conference.mediaPipeline);
				var sdpOffer = receiveLeftEndpoint.generateOffer(createReceiveOnlyOfferOptions());
				entry.getValue().receiveConnectionByParticipantKey.put(
						participantKey,
						new ReceiveConnection(receiveLeftEndpoint, sdpOffer, gatherCandidatesReactive(receiveLeftEndpoint)));
			}
			if (isReceiving) {
				var receiveRightEndpoint = createIntermediateEndpoint(sendEndpoint, conference.mediaPipeline);
				var sdpOffer = receiveRightEndpoint.generateOffer(createReceiveOnlyOfferOptions());
				receiveConnectionBySipURI.put(
						entry.getKey(),
						new ReceiveConnection(receiveRightEndpoint, sdpOffer, gatherCandidatesReactive(receiveRightEndpoint)));
			}
		}
		conference.webRtcEndpointMap().put(participantKey, new UserMediaContext(webRtcEndpoint, receiveConnectionBySipURI));
		return new ConferenceJoinResponse(sdpResponse, gatherCandidatesReactive(webRtcEndpoint));
	}

	private String calculateParticipantKey(ConferenceJoinRequest conferenceJoinRequest) {
		return conferenceJoinRequest.sipURI().getURIAsString() + conferenceJoinRequest.disambiguator();
	}

	private Flux<String> gatherCandidatesReactive(WebRtcEndpoint webRtcEndpoint) {
		return Flux.create(sink -> {
			webRtcEndpoint.addIceCandidateFoundListener(event -> {
				LOGGER.debug("Ice candidate found {}", event.getCandidate());
				sink.next(event.getCandidate().getCandidate());
			});
			webRtcEndpoint.addIceGatheringDoneListener(event -> {
				LOGGER.debug("Gathering completed!");
				sink.complete();
			});
			webRtcEndpoint.gatherCandidates();
		});
	}

	private WebRtcEndpoint create(MediaPipeline mediaPipeline) {
		var webRtcEndpoint = new WebRtcEndpoint.Builder(mediaPipeline).build();
		webRtcEndpoint.setStunServerAddress("stun.l.google.com");
		webRtcEndpoint.setStunServerPort(19302);
		return webRtcEndpoint;
	}

	private WebRtcEndpoint createIntermediateEndpoint(WebRtcEndpoint source, MediaPipeline mediaPipeline) {
		var intermediateEndpoint = createEndpointInConference(mediaPipeline);
		source.connect(intermediateEndpoint, MediaType.AUDIO);
		source.connect(intermediateEndpoint, MediaType.VIDEO);
		return intermediateEndpoint;
	}

	private WebRtcEndpoint createEndpointInConference(MediaPipeline mediaPipeline) {
		var webRtcEndpoint = new WebRtcEndpoint.Builder(mediaPipeline).build();
		webRtcEndpoint.setStunServerAddress("stun.l.google.com");
		webRtcEndpoint.setStunServerPort(19302);
		webRtcEndpoint.gatherCandidates();
		return webRtcEndpoint;
	}

	@Override
	public boolean isConference(String conferenceId) {
		return mediaPipelineByConferenceId.containsKey(conferenceId);
	}

	@Override
	public Flux<Participant> getParticipantsFromPerspectiveOf(String conferenceId, SipURI referenceURI) {
		var conference = mediaPipelineByConferenceId.get(conferenceId);
		if (conference == null) {
			return Flux.empty();
		}
		UserMediaContext userMediaContext = conference.webRtcEndpointMap()
				.get(referenceURI.getURIAsString());
		LOGGER.info("Get participants from perspective {} = {}", referenceURI, userMediaContext
				.receiveConnectionByParticipantKey());
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

	@Override
	public Mono<Void> processAnswersReactive(String conferenceId, SipURI referenceURI, Map<String, String> sdpAnswerByParticipantKey) {
		return Mono.fromCallable(() -> {
			LOGGER.info("Processing answer reactive...");
			var conference = getConference(conferenceId);
			var userMediaContext = conference.webRtcEndpointMap().get(referenceURI.getURIAsString());
			for (var entry : sdpAnswerByParticipantKey.entrySet()) {
				var receiveConnection = userMediaContext.receiveConnectionByParticipantKey().get(entry.getKey());
				if (receiveConnection == null) {
					continue;
				}
				LOGGER.info("Processing answer for {}", entry.getKey());
				final String processAnswer = receiveConnection.endpoint().processAnswer(entry.getValue());
				LOGGER.info("Process answer result = {}", processAnswer);
			}
			return null;
		});
	}

	@Override
	public void processAnswers(String conferenceId, SipURI referenceURI, Map<String, String> sdpAnswerByParticipantKey) {
		var conference = getConference(conferenceId);
		var userMediaContext = conference.webRtcEndpointMap().get(referenceURI.getURIAsString());
		for (var entry : sdpAnswerByParticipantKey.entrySet()) {
			final ReceiveConnection receiveConnection = userMediaContext.receiveConnectionByParticipantKey().get(entry.getKey());
			if (receiveConnection == null) {
				continue;
			}
			LOGGER.info("Processing answer for {}", entry.getKey());
			final String processAnswer = receiveConnection.endpoint().processAnswer(entry.getValue());
			LOGGER.info("Process answer result = {}", processAnswer);
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

	private record Conference(MediaPipeline mediaPipeline, Map<String, UserMediaContext> webRtcEndpointMap) {
	}

	private record ReceiveConnection(WebRtcEndpoint endpoint, String sdpOffer, Flux<String> iceCandidates) {
	}

	private record UserMediaContext(WebRtcEndpoint sendRTCEndpoint, Map<String, ReceiveConnection> receiveConnectionByParticipantKey) {
	}

}
