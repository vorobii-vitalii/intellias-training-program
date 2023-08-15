package sip.request_handling.invite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.IceCandidateFoundEvent;
import org.kurento.client.IceGatheringDoneEvent;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaType;
import org.kurento.client.OfferOptions;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sip.SipURI;

// Screensharing, only view
// Add Jaeger
// Change approach for ICE gathering
// Reactive streams
public class KurentoMediaConferenceService implements MediaConferenceService {
	private static final Logger LOGGER = LoggerFactory.getLogger(KurentoMediaConferenceService.class);
	private static final int ICE_CANDIDATES_GATHER_TIMEOUT = 2000;
	private static final String DELIMITER = "_";
	private final Map<String, Conference> mediaPipelineByConferenceId = new ConcurrentHashMap<>();

	private final KurentoClient kurentoClient;

	public KurentoMediaConferenceService(KurentoClient kurentoClient) {
		this.kurentoClient = kurentoClient;
	}

	@Override
	public void createNewConference(String conferenceId) {
		var mediaPipeline = kurentoClient.createMediaPipeline();
		// TODO: Call this function when conference ends
		// mediaPipeline.release();
		mediaPipelineByConferenceId.put(conferenceId, new Conference(mediaPipeline, new ConcurrentHashMap<>()));
	}

	@Override
	public String connectToConference(ConferenceJoinRequest conferenceJoinRequest) {
		var conference = getConference(conferenceJoinRequest.conferenceId());
		var webRtcEndpoint = create(conference.mediaPipeline);
		webRtcEndpoint.gatherCandidates();
		var sdpResponse = webRtcEndpoint.processOffer(conferenceJoinRequest.sdpOffer());
		var participantKey = calculateParticipantKey(conferenceJoinRequest);
		var isReceiving = conferenceJoinRequest.mode().receive();
		var isSending = conferenceJoinRequest.mode().send();
		var receiveConnectionBySipURI = new ConcurrentHashMap<String, ReceiveConnection>();
		for (var entry : conference.webRtcEndpointMap().entrySet()) {
			var sendEndpoint = entry.getValue().sendRTCEndpoint();
			if (isSending) {
				var receiveLeftEndpoint = createIntermediateEndpoint(sendEndpoint, conference.mediaPipeline);
				entry.getValue().receiveConnectionByParticipantKey.put(
						participantKey,
						new ReceiveConnection(receiveLeftEndpoint, addIceCandidates(
								receiveLeftEndpoint.generateOffer(createReceiveOnlyOfferOptions()),
								gatherCandidates(receiveLeftEndpoint)
						)));
			}
			if (isReceiving) {
				var receiveRightEndpoint = createIntermediateEndpoint(webRtcEndpoint, conference.mediaPipeline);
				receiveConnectionBySipURI.put(
						entry.getKey(),
						new ReceiveConnection(receiveRightEndpoint, addIceCandidates(
								receiveRightEndpoint.generateOffer(createReceiveOnlyOfferOptions()),
								gatherCandidates(receiveRightEndpoint)
						)));
			}
		}
		conference.webRtcEndpointMap().put(participantKey, new UserMediaContext(webRtcEndpoint, receiveConnectionBySipURI));
		var sdpResponseWithCandidates = addIceCandidates(sdpResponse, gatherCandidates(webRtcEndpoint));
		LOGGER.info("SDP with candidates = {}", sdpResponseWithCandidates);
		return sdpResponseWithCandidates;
	}

	private String calculateParticipantKey(ConferenceJoinRequest conferenceJoinRequest) {
		return conferenceJoinRequest.sipURI().getURIAsString() + conferenceJoinRequest.disambiguator();
	}

	private String addIceCandidates(String originalSDP, List<IceCandidate> iceCandidates) {
		var rightPart = new ArrayList<>(iceCandidates).stream()
				.map(s -> "a=" + s.getCandidate())
				.collect(Collectors.joining("\r\n", "", "\r\n"));

		return originalSDP + rightPart;
	}

	private List<IceCandidate> gatherCandidates(WebRtcEndpoint webRtcEndpoint) {
		List<IceCandidate> iceCandidates = Collections.synchronizedList(new ArrayList<>());
		webRtcEndpoint.addIceCandidateFoundListener(event -> {
			LOGGER.info("Ice candidate found (create) {}", event.getCandidate());
			iceCandidates.add(event.getCandidate());
		});
		webRtcEndpoint.gatherCandidates();
		var countDownLatch = new CountDownLatch(1);
		webRtcEndpoint.addIceGatheringDoneListener(event -> {
			LOGGER.info("Gathering done!");
			countDownLatch.countDown();
		});
		try {
			countDownLatch.await(ICE_CANDIDATES_GATHER_TIMEOUT, TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		return iceCandidates;
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
		webRtcEndpoint.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {
			@Override
			public void onEvent(IceCandidateFoundEvent event) {
				LOGGER.info("Ice candidate found {}", event.getCandidate());
			}
		});
		webRtcEndpoint.addIceGatheringDoneListener(new EventListener<IceGatheringDoneEvent>() {
			@Override
			public void onEvent(IceGatheringDoneEvent event) {
				LOGGER.info("SDP after gathering 123");
			}
		});
		webRtcEndpoint.gatherCandidates();
		LOGGER.info("Gathered candidates = {}", webRtcEndpoint.getICECandidatePairs());
		return webRtcEndpoint;
	}

	@Override
	public boolean isConference(String conferenceId) {
		return mediaPipelineByConferenceId.containsKey(conferenceId);
	}

	@Override
	public List<Participant> getParticipantsFromPerspectiveOf(String conferenceId, SipURI referenceURI) {
		var conference = mediaPipelineByConferenceId.get(conferenceId);
		if (conference == null) {
			return List.of();
		}
		LOGGER.info("Get participants from perspective {}", conference.webRtcEndpointMap());
		UserMediaContext userMediaContext = conference.webRtcEndpointMap()
				.get(referenceURI.getURIAsString());
		if (userMediaContext == null) {
			return List.of();
		}
		return userMediaContext
				.receiveConnectionByParticipantKey()
				.entrySet()
				.stream()
				.map(e -> {
					var sdpOffer = e.getValue().sdpOffer();
					var participantKey = e.getKey();
					return new Participant(participantKey, sdpOffer);
				})
				.collect(Collectors.toList());
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

	private static OfferOptions createReceiveOnlyOfferOptions() {
		var offerOptions = new OfferOptions();
		offerOptions.setOfferToReceiveAudio(true);
		offerOptions.setOfferToReceiveVideo(true);
		return offerOptions;
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

	private record ReceiveConnection(WebRtcEndpoint endpoint, String sdpOffer) {
	}

	private record UserMediaContext(WebRtcEndpoint sendRTCEndpoint, Map<String, ReceiveConnection> receiveConnectionByParticipantKey) {
	}


}
