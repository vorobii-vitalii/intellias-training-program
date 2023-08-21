package sip.reactor_netty;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import util.Serializable;

public interface WSOutbound {
	Publisher<Void> send(Flux<Serializable> objectsPublisher);
}
