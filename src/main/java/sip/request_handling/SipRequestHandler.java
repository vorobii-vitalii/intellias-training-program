package sip.request_handling;

import sip.SipRequest;

public interface SipRequestHandler extends SipMessageHandler<SipRequest> {
	String getHandledType();
}
