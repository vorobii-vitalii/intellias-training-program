package sip.request_handling;

import sip.SipRequestHeaders;

public record NewEventRequest(String callId, String methodName, SipRequestHeaders overrideHeaders, byte[] body, SubscriptionState newState) {

	public enum SubscriptionState {
		ACTIVE,
		DESTROYED
	}

}
