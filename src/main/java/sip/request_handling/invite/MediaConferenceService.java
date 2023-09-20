package sip.request_handling.invite;

import java.util.Map;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sip.FullSipURI;

public interface MediaConferenceService {
	Mono<Void> createNewConferenceReactive(String conferenceId);
	void createNewConference(String conferenceId);
	ConferenceJoinResponse connectToConferenceReactive(ConferenceJoinRequest conferenceJoinRequest);
	Mono<Void> disconnectFromConference(ConferenceDisconnectRequest conferenceDisconnectRequest);
	boolean isConference(String conferenceId);
	Flux<Participant> getParticipantsFromPerspectiveOf(String conferenceId, FullSipURI referenceURI);
	void processAnswers(String conferenceId, FullSipURI referenceURI, Map<String, String> sdpAnswerBySipURI);
}
