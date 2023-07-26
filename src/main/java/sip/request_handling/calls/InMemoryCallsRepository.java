package sip.request_handling.calls;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jcip.annotations.ThreadSafe;

@ThreadSafe
public class InMemoryCallsRepository implements CallsRepository {
	private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryCallsRepository.class);

	private final Map<String, CallDetails> callDetailsMap = new ConcurrentHashMap<>();

	@Override
	public CallDetails getCallDetailsByCallId(@Nonnull String callId) {
		return callDetailsMap.get(callId);
	}

	@Nonnull
	@Override
	public CallDetails upsert(@Nonnull String callId) {
		return callDetailsMap.compute(callId, (
				s, callDetails) -> callDetails != null ? callDetails : createInitialCallDetails(callId));
	}

	@Override
	public void update(@Nonnull String callId, @Nonnull CallDetails newCallDetails) {
		LOGGER.info("Updating callId = {} details = {}", callId, newCallDetails);
		callDetailsMap.put(callId, newCallDetails);
	}

	@Override
	public void remove(@Nonnull String callId) {
		LOGGER.info("Removing callId = {}", callId);
		callDetailsMap.remove(callId);
	}

	private CallDetails createInitialCallDetails(String callId) {
		return new CallDetails(callId, new HashMap<>(), new HashSet<>(), null, CallState.CREATED);
	}

}
