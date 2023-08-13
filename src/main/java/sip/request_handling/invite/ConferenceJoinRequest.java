package sip.request_handling.invite;

import javax.annotation.Nonnull;

import sip.SipURI;

public record ConferenceJoinRequest(
		@Nonnull
		String conferenceId,
		@Nonnull
		SipURI sipURI,
		@Nonnull
		String sdpOffer,
		@Nonnull
		String disambiguator,
		@Nonnull
		Mode mode
) {
}
