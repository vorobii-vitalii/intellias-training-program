package sip.reactor_netty.service;

import java.util.Collection;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sip.AddressOfRecord;
import sip.reactor_netty.WSOutbound;
import sip.request_handling.register.CreateBinding;

public interface ReactiveBindingStorage {
	Flux<WSOutbound> getOutboundsByAddressOfRecord(AddressOfRecord addressOfRecord);
	Mono<Void> removeBindingsByAddressOfRecord(AddressOfRecord addressOfRecord);
	Mono<Void> addBindings(WSOutbound wsOutbound, AddressOfRecord addressOfRecord, Collection<CreateBinding> newBindings, int defaultExpiration);
	Flux<AddressOfRecord> getAllBindingsByAddressOfRecord(AddressOfRecord addressOfRecord);
}
