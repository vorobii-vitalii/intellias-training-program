package sip.request_handling;

import java.util.Set;

import sip.SipRequest;

public interface SipRequestHandler extends SipMessageHandler<SipRequest> {
	Set<String> getHandledTypes();
}
