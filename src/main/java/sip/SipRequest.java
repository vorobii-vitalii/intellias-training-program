package sip;

public record SipRequest(SipRequestLine requestLine, SipHeaders headers, byte[] payload) {
}
