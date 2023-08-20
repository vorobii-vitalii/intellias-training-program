package sip.request_handling.invite;

import java.util.List;
import java.util.Map;

import reactor.core.publisher.Flux;
import sip.SipURI;

public interface MediaConferenceService {
	void createNewConference(String conferenceId);
	ConferenceJoinResponse connectToConferenceReactive(ConferenceJoinRequest conferenceJoinRequest);
	boolean isConference(String conferenceId);
	Flux<Participant> getParticipantsFromPerspectiveOf(String conferenceId, SipURI referenceURI);
	void processAnswers(String conferenceId, SipURI referenceURI, Map<String, String> sdpAnswerBySipURI);
}