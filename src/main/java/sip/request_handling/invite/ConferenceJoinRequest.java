package sip.request_handling.invite;

import javax.annotation.Nonnull;

import sip.FullSipURI;

public record ConferenceJoinRequest(
		@Nonnull
		String conferenceId,
		@Nonnull
		FullSipURI sipURI,
		@Nonnull
		String sdpOffer,
		@Nonnull
		String disambiguator,
		@Nonnull
		Mode mode
) {
}
