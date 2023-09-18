package sip.request_handling.invite;

import sip.FullSipURI;

public record JoinedParticipant(FullSipURI sipURI, Mode mode, String disambiguator) {
}
