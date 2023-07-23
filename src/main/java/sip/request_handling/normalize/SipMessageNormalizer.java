package sip.request_handling.normalize;

import sip.SipMessage;

public interface SipMessageNormalizer<T extends SipMessage, C> {
	T normalize(T message, C context);
}
