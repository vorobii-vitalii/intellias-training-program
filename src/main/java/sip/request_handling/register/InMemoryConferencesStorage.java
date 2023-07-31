package sip.request_handling.register;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.jcip.annotations.ThreadSafe;

@ThreadSafe
public class InMemoryConferencesStorage implements ConferencesStorage {
	private final Map<String, Object> conferencesMap = new ConcurrentHashMap<>();

	@Override
	public void createNewConference(String conferenceId) {
		conferencesMap.put(conferenceId, "123");
	}

	@Override
	public boolean isConference(String potentialConferenceId) {
		return conferencesMap.containsKey(potentialConferenceId);
	}
}
