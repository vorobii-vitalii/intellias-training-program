package sip.reactor_netty.service.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sip.AddressOfRecord;
import sip.reactor_netty.WSOutbound;
import sip.reactor_netty.service.ReactiveBindingStorage;
import sip.request_handling.register.CreateBinding;

public class InMemoryReactiveBindingStorage implements ReactiveBindingStorage {
	private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryReactiveBindingStorage.class);
	private static final String EXPIRES = "expires";
	private final Map<AddressOfRecord, Map<AddressOfRecord, BindingInfo>> bindings = new ConcurrentHashMap<>();

	@Override
	public Flux<WSOutbound> getOutboundsByAddressOfRecord(AddressOfRecord addressOfRecord) {
		return Flux.generate(sink -> {
			LOGGER.info("Getting outbounds by address of record = {}", addressOfRecord);
			Optional.ofNullable(bindings.get(addressOfRecord))
					.stream()
					.flatMap(map -> map.values().stream())
					.map(BindingInfo::wsOutbound)
					.forEach(sink::next);
			sink.complete();
		});
	}

	@Override
	public Mono<Void> removeBindingsByAddressOfRecord(AddressOfRecord addressOfRecord) {
		return Mono.create(sink -> {
			bindings.remove(addressOfRecord);
			sink.success();
		});
	}

	@Override
	public Mono<Void> addBindings(
			WSOutbound wsOutbound,
			AddressOfRecord addressOfRecord,
			Collection<CreateBinding> newBindings,
			int defaultExpiration
	) {
		return Mono.create(sink -> {
			var newBindingMap = bindings.getOrDefault(addressOfRecord, new HashMap<>());
			for (var binding : newBindings) {
				var bindingRecord = binding.bindingRecord();
				var bindingInfo = newBindingMap.get(bindingRecord);
				// If the binding does not exist, it is tentatively added.
				if (bindingInfo == null) {
					newBindingMap.put(bindingRecord, calculateBindingInfo(binding, defaultExpiration, wsOutbound));
				} else {
					if (!bindingInfo.canBeOverriden(binding)) {
						sink.error(new IllegalStateException("Binding " + binding + " cannot be added"));
						break;
					}
					newBindingMap.put(bindingRecord, calculateBindingInfo(binding, defaultExpiration, wsOutbound));
				}
			}
			LOGGER.info("New bindings for AOR = {} {}", addressOfRecord, newBindingMap);
			bindings.put(addressOfRecord, newBindingMap);
			sink.success();
		});
	}

	@Override
	public Flux<AddressOfRecord> getAllBindingsByAddressOfRecord(AddressOfRecord addressOfRecord) {
		return Flux.fromStream(() -> Optional.ofNullable(bindings.get(addressOfRecord))
				.stream()
				.flatMap(v -> v.keySet().stream()));
	}

	private BindingInfo calculateBindingInfo(CreateBinding createBinding, int defaultExpiration, WSOutbound wsOutbound) {
		int expirationInSeconds = createBinding.bindingRecord()
				.getParameterValue(EXPIRES)
				.map(String::trim)
				.map(Integer::parseInt)
				.orElse(defaultExpiration);
		return new BindingInfo(
				createBinding.callId(),
				createBinding.commandSequence(),
				Instant.now().plusSeconds(expirationInSeconds),
				wsOutbound
		);
	}


	private record BindingInfo(String callId, int commandSequence, Instant expirationTimestamp, WSOutbound wsOutbound) {
		public boolean canBeOverriden(CreateBinding createBinding) {
			if (!createBinding.callId().equals(callId)) {
				return true;
			}
			return createBinding.commandSequence() > commandSequence;
		}
	}

}
