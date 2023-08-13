package sip.request_handling.invite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.kurento.client.Continuation;
import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.IceCandidateFoundEvent;
import org.kurento.client.IceComponentStateChangedEvent;
import org.kurento.client.IceGatheringDoneEvent;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaFlowInStateChangedEvent;
import org.kurento.client.MediaFlowOutStateChangedEvent;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaProfileSpecType;
import org.kurento.client.MediaState;
import org.kurento.client.MediaStateChangedEvent;
import org.kurento.client.MediaType;
import org.kurento.client.OfferOptions;
import org.kurento.client.PlayerEndpoint;
import org.kurento.client.RecorderEndpoint;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.jsdp.Attribute;
import net.sourceforge.jsdp.SDPException;
import net.sourceforge.jsdp.SDPFactory;
import net.sourceforge.jsdp.SDPParseException;
import net.sourceforge.jsdp.SessionDescription;
import sip.SipURI;

public class KurentoMediaConferenceService implements MediaConferenceService {
	private static final Logger LOGGER = LoggerFactory.getLogger(KurentoMediaConferenceService.class);
	private final Map<String, Conference> mediaPipelineByConferenceId = new ConcurrentHashMap<>();

	private final KurentoClient kurentoClient;
	private AtomicReference<RecorderEndpoint> recorderEndpoint = new AtomicReference<>();


	public KurentoMediaConferenceService(KurentoClient kurentoClient) {
		this.kurentoClient = kurentoClient;
	}

	@Override
	public void createNewConference(String conferenceId) {
		var mediaPipeline = kurentoClient.createMediaPipeline();
		// TODO: Call this when conference ends
		// mediaPipeline.release();
		mediaPipelineByConferenceId.put(conferenceId, new Conference(mediaPipeline, new ConcurrentHashMap<>()));
	}

	private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();



	@Override
	public String connectToConference(String conferenceId, SipURI sipURI, String sdpOffer) {
		var conference = getConference(conferenceId);
		var webRtcEndpoint = create(conference.mediaPipeline);
		webRtcEndpoint.gatherCandidates();
		var sdpResponse = webRtcEndpoint.processOffer(sdpOffer);
		LOGGER.info("Initial SDP response = {}", sdpResponse);
		webRtcEndpoint.addMediaStateChangedListener(event -> LOGGER.info("Conference = {} sipURI = {} media state changed from {} to {}",
				conference,
				sipURI.getURIAsString(),
				event.getOldState(),
				event.getNewState()
		));

		var receiveConnectionBySipURI = new ConcurrentHashMap<String, ReceiveConnection>();
		// TODO: Add lock
		for (var entry : conference.webRtcEndpointMap().entrySet()) {
			var sendEndpoint = entry.getValue().sendRTCEndpoint();
			var receiveLeftEndpoint = createIntermediateEndpoint(sendEndpoint, conference.mediaPipeline);
			var receiveRightEndpoint = createIntermediateEndpoint(webRtcEndpoint, conference.mediaPipeline);
			entry.getValue().receiveConnectionBySipURI.put(
					sipURI.getURIAsString(),
					new ReceiveConnection(receiveLeftEndpoint, addIceCandidates(
							receiveLeftEndpoint.generateOffer(createReceiveOnlyOfferOptions()),
							gatherCandidates(receiveLeftEndpoint)
					)));
			receiveConnectionBySipURI.put(
					entry.getKey(),
					new ReceiveConnection(receiveRightEndpoint, addIceCandidates(
							receiveRightEndpoint.generateOffer(createReceiveOnlyOfferOptions()),
							gatherCandidates(receiveRightEndpoint)
					)));
		}
		// Screensharing, only view
		// Add Jaeger
		// Change approach for ICE gathering
		// Reactive streams
		conference.webRtcEndpointMap().put(sipURI.getURIAsString(), new UserMediaContext(webRtcEndpoint, receiveConnectionBySipURI));
		var sdpResponseWithCandidates = addIceCandidates(sdpResponse, gatherCandidates(webRtcEndpoint));
		LOGGER.info("SDP with candidates = {}", sdpResponseWithCandidates);
		return sdpResponseWithCandidates;
	}

	private String addIceCandidates(String originalSDP, List<IceCandidate> iceCandidates) {
		var rightPart = new ArrayList<>(iceCandidates).stream()
				.map(s -> {
					return "a=" + s.getCandidate();
				})
				.collect(Collectors.joining("\r\n", "", "\r\n"));

		return originalSDP + rightPart;
//		try {
//
////
////			var sessionDescription = SDPFactory.parseSessionDescription(originalSDP);
////			for (var iceCandidate : iceCandidates) {
////				sessionDescription.addAttribute(new Attribute(
////						"candidate",
////						iceCandidate.getSdpMid() + ":" + iceCandidate.getSdpMLineIndex() + " " + iceCandidate.getCandidate()
////				));
////			}
////			return sessionDescription.toString();
//		}
//		catch (SDPException e) {
//			throw new RuntimeException(e);
//		}
	}

	private List<IceCandidate> gatherCandidates(WebRtcEndpoint webRtcEndpoint) {
		List<IceCandidate> iceCandidates = Collections.synchronizedList(new ArrayList<>());
		webRtcEndpoint.addIceCandidateFoundListener(event -> {
			LOGGER.info("Ice candidate found (create) {}", event.getCandidate());
			iceCandidates.add(event.getCandidate());
		});
		webRtcEndpoint.gatherCandidates();
		CountDownLatch countDownLatch = new CountDownLatch(1);
		webRtcEndpoint.addIceGatheringDoneListener(event -> {
			LOGGER.info("Gathering done!");
			countDownLatch.countDown();
		});
		try {
			countDownLatch.await(2000, TimeUnit.MILLISECONDS);
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
		var conference = getConference(conferenceId);
		LOGGER.info("Get participants from perspective {}", conference.webRtcEndpointMap());
		return conference.webRtcEndpointMap()
				.get(referenceURI.getURIAsString())
				.receiveConnectionBySipURI()
				.entrySet()
				.stream()
				.map(e -> {
					var sdpOffer = e.getValue().sdpOffer();
					var sipURI = e.getKey();
					return new Participant(sipURI, sdpOffer);
				})
				.collect(Collectors.toList());
	}

	@Override
	public void processAnswers(String conferenceId, SipURI referenceURI, Map<String, String> sdpAnswerBySipURI) {
		var conference = getConference(conferenceId);
		var userMediaContext = conference.webRtcEndpointMap().get(referenceURI.getURIAsString());
		for (var entry : sdpAnswerBySipURI.entrySet()) {
			final ReceiveConnection receiveConnection = userMediaContext.receiveConnectionBySipURI().get(entry.getKey());
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

	private record UserMediaContext(WebRtcEndpoint sendRTCEndpoint, Map<String, ReceiveConnection> receiveConnectionBySipURI) {
	}


}
