package sip.request_handling;

import sip.SipRequestHeaders;

public record DialogRequest(String callId, String methodName, SipRequestHeaders overrideHeaders, byte[] body) {
}
