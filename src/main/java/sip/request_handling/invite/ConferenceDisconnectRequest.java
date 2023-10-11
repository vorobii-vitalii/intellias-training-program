package sip.request_handling.invite;

import javax.annotation.Nonnull;

import sip.FullSipURI;

public record ConferenceDisconnectRequest(
		@Nonnull
		String conferenceId,
		@Nonnull
		FullSipURI sipURI,
		@Nonnull
		String disambiguator
) {
}
