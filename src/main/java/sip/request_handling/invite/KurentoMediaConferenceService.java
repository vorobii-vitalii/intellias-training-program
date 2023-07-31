package sip.request_handling.invite;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.Properties;
import org.kurento.client.WebRtcEndpoint;

public class KurentoMediaConferenceService implements MediaConferenceService {
	private final Map<String, MediaPipeline> mediaPipelineByConferenceId = new ConcurrentHashMap<>();

	private final KurentoClient kurentoClient;

	public KurentoMediaConferenceService(KurentoClient kurentoClient) {
		this.kurentoClient = kurentoClient;
	}

	@Override
	public void createNewConference(String conferenceId) {
		var mediaPipeline = kurentoClient.createMediaPipeline();
		// TODO: Call this when conference ends
		// mediaPipeline.release();
		mediaPipelineByConferenceId.put(conferenceId, mediaPipeline);
//		var webRtcEndpoint = new WebRtcEndpoint.Builder(mediaPipeline).build();
//		webRtcEndpoint.processOffer()
	}

	@Override
	public String establishMediaSession(String conferenceId, String sdpOffer) {
		var mediaPipeline = mediaPipelineByConferenceId.get(conferenceId);
		if (mediaPipeline == null) {
			throw new IllegalArgumentException("Conference " + conferenceId + " doesn't exist!");
		}
		var webRtcEndpoint = new WebRtcEndpoint.Builder(mediaPipeline).build();
		return webRtcEndpoint.processOffer(sdpOffer);
	}
}
