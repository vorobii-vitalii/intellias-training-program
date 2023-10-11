package sip.request_handling.invite;

import reactor.core.publisher.Flux;

public record ConferenceJoinResponse(String sdpAnswer, Flux<String> iceCandidates) {
}
