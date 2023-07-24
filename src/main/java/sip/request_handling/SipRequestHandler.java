package sip.request_handling;

import java.util.Set;

import sip.SipRequest;

public interface SipRequestHandler extends SipMessageHandler<SipRequest> {
	String getHandledType();

	public default Set<String> getHandledTypes() {
		return Set.of(getHandledType());
	}

}
