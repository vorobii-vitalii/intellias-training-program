package sip.request_handling.register;

public interface ConferencesStorage {
	void createNewConference(String conferenceId);
	boolean isConference(String potentialConferenceId);
}
