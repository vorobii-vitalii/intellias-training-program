package sip.request_handling.invite;

import reactor.core.publisher.Flux;

public record Participant(String participantKey, String sdpOffer, Flux<String> iceCandidates) {
}
