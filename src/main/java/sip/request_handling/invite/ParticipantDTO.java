package sip.request_handling.invite;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record ParticipantDTO(String participantKey, String sdpOffer) {

	public static ParticipantDTO create(String participantKey, String sdpOffer, List<String> iceCandidates) {
		return new ParticipantDTO(participantKey, addIceCandidates(sdpOffer, iceCandidates.stream()));
	}

	private static String addIceCandidates(String originalSDP, Stream<String> iceCandidates) {
		var rightPart = iceCandidates
				.map(s -> "a=" + s)
				.collect(Collectors.joining("\r\n", "", "\r\n"));
		return originalSDP + rightPart;
	}

}
