package sip.request_handling.calls;

import javax.annotation.Nonnull;

public interface CallsRepository {
	CallDetails getCallDetailsByCallId(@Nonnull String callId);
	@Nonnull
	CallDetails upsert(@Nonnull String callId);
	void update(@Nonnull String callId, @Nonnull CallDetails newCallDetails);
	void remove(@Nonnull String callId);
}
