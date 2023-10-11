package sip.request_handling;

import sip.SipMediaType;

public record SipSessionDescription(String description, SipMediaType sipMediaType) {
}
