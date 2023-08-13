package sip.request_handling.invite;

import sip.SipURI;

public record JoinedParticipant(SipURI sipURI, Mode mode, String disambiguator) {
}
