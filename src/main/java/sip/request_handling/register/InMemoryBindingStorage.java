package sip.request_handling.register;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jcip.annotations.ThreadSafe;
import sip.AddressOfRecord;
import tcp.server.SocketConnection;

@ThreadSafe
public class InMemoryBindingStorage implements BindingStorage {
	private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryBindingStorage.class);
	private static final String EXPIRES = "expires";
	private final Map<AddressOfRecord, Map<AddressOfRecord, BindingInfo>> bindings = new ConcurrentHashMap<>();

	@Override
	public Set<SocketConnection> getConnectionsByAddressOfRecord(AddressOfRecord addressOfRecord) {
		LOGGER.info("Getting connections for AOR = {}", addressOfRecord);
		return Optional.ofNullable(bindings.get(addressOfRecord))
				.stream()
				.flatMap(map -> map.values().stream())
				.map(BindingInfo::socketConnection)
				.collect(Collectors.toSet());
	}

	@Override
	public void removeBindingsByAddressOfRecord(AddressOfRecord addressOfRecord) {
		bindings.remove(addressOfRecord);
	}

	@Override
	public void addBindings(
			SocketConnection socketConnection,
			AddressOfRecord addressOfRecord,
			Collection<CreateBinding> newBindings,
			int defaultExpiration
	) {
		var newBindingMap = bindings.getOrDefault(addressOfRecord, new HashMap<>());
		for (var binding : newBindings) {
			var bindingRecord = binding.bindingRecord();
			var bindingInfo = newBindingMap.get(bindingRecord);
			// If the binding does not exist, it is tentatively added.
			if (bindingInfo == null) {
				newBindingMap.put(bindingRecord, calculateBindingInfo(binding, defaultExpiration, socketConnection));
			} else {
				if (!bindingInfo.canBeOverriden(binding)) {
					throw new IllegalStateException("Binding " + binding + " cannot be added");
				}
				newBindingMap.put(bindingRecord, calculateBindingInfo(binding, defaultExpiration, socketConnection));
			}
		}
		LOGGER.info("New bindings for AOR = {} {}", addressOfRecord, newBindingMap);
		bindings.put(addressOfRecord, newBindingMap);
	}

	@Override
	public Set<AddressOfRecord> getCurrentBindings(AddressOfRecord addressOfRecord) {
		return Optional.ofNullable(bindings.get(addressOfRecord))
				.map(Map::keySet)
				.orElse(Set.of());
	}

	private BindingInfo calculateBindingInfo(CreateBinding createBinding, int defaultExpiration, SocketConnection socketConnection) {
		int expirationInSeconds = createBinding.bindingRecord()
				.getParameterValue(EXPIRES)
				.map(String::trim)
				.map(Integer::parseInt)
				.orElse(defaultExpiration);
		return new BindingInfo(
				createBinding.callId(),
				createBinding.commandSequence(),
				Instant.now().plusSeconds(expirationInSeconds),
				socketConnection
		);
	}

	private record BindingInfo(String callId, int commandSequence, Instant expirationTimestamp, SocketConnection socketConnection) {
		public boolean canBeOverriden(CreateBinding createBinding) {
			if (!createBinding.callId().equals(callId)) {
				return true;
			}
			return createBinding.commandSequence() > commandSequence;
		}
	}

}
