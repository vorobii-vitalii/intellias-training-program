package sip;

public sealed interface SipMessage permits SipRequest, SipResponse {
}
