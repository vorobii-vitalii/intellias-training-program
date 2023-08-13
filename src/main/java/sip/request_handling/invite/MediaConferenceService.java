package sip.request_handling.invite;

import java.util.List;
import java.util.Map;

import sip.SipURI;

public interface MediaConferenceService {
	void createNewConference(String conferenceId);
	String connectToConference(ConferenceJoinRequest conferenceJoinRequest);
	String connectToConference(String conferenceId, SipURI sipURI, String sdpOffer);
	boolean isConference(String conferenceId);
	List<Participant> getParticipantsFromPerspectiveOf(String conferenceId, SipURI referenceURI);
	void processAnswers(String conferenceId, SipURI referenceURI, Map<String, String> sdpAnswerBySipURI);
}
