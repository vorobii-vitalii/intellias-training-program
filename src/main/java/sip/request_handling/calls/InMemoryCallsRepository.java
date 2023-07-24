package sip.request_handling.calls;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import net.jcip.annotations.ThreadSafe;

@ThreadSafe
public class InMemoryCallsRepository implements CallsRepository {
	private final Map<String, CallDetails> callDetailsMap = new ConcurrentHashMap<>();

	@Override
	public CallDetails getCallDetailsByCallId(@Nonnull String callId) {
		return callDetailsMap.get(callId);
	}

	@Nonnull
	@Override
	public CallDetails upsert(@Nonnull String callId) {
		return callDetailsMap.compute(callId, (
				s, callDetails) -> callDetails != null ? callDetails : new CallDetails(callId, new HashMap<>(), new HashSet<>(), null));
	}

	@Override
	public void update(@Nonnull String callId, @Nonnull CallDetails newCallDetails) {
		callDetailsMap.put(callId, newCallDetails);
	}

	@Override
	public void remove(@Nonnull String callId) {
		callDetailsMap.remove(callId);
	}
}
