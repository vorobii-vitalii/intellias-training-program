package sip.request_handling.invite;

import javax.annotation.Nonnull;

import sip.SipURI;

public record ConferenceDisconnectRequest(
		@Nonnull
		String conferenceId,
		@Nonnull
		SipURI sipURI,
		@Nonnull
		String disambiguator
) {
}
