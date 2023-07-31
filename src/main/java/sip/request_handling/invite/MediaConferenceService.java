package sip.request_handling.invite;

public interface MediaConferenceService {
	void createNewConference(String conferenceId);

	// Returns SDP answer
	String establishMediaSession(String conferenceId, String sdpOffer);
}
