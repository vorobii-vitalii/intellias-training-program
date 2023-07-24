package sip.request_handling.media;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.jcip.annotations.ThreadSafe;
import sip.Address;

@ThreadSafe
public class InMemoryMediaMappingStorage implements MediaMappingStorage {
	private final Map<String, Set<Address>> sourceAddressesBySessionId = new ConcurrentHashMap<>();
	private final Map<Address, Map<String, Set<Address>>> mediaMappingsBySourceAddress = new ConcurrentHashMap<>();

	@Override
	public void addMapping(Address source, Address destination, String mediaType, String sessionId) {
		sourceAddressesBySessionId.compute(sessionId, (s, addresses) -> {
			if (addresses == null) {
				addresses = new HashSet<>();
			}
			addresses.add(source);
			return addresses;
		});
		mediaMappingsBySourceAddress.compute(source, (address, addressesByMediaType) -> {
			if (addressesByMediaType == null) {
				addressesByMediaType = new HashMap<>();
			}
			addressesByMediaType.compute(mediaType, (s, addresses) -> {
				if (addresses == null) {
					addresses = new HashSet<>();
				}
				addresses.add(destination);
				return addresses;
			});
			return addressesByMediaType;
		});
	}

	@Override
	public void invalidate(String sessionId) {
		var sourceAddresses = sourceAddressesBySessionId.getOrDefault(sessionId, Set.of());
		for (var sourceAddress : sourceAddresses) {
			mediaMappingsBySourceAddress.remove(sourceAddress);
		}
		sourceAddressesBySessionId.remove(sessionId);
	}

	@Override
	public Collection<Address> getReceivers(Address source, String mediaType) {
		var map = mediaMappingsBySourceAddress.get(source);
		if (map == null) {
			return Set.of();
		}
		var addresses = map.get(mediaType);
		return addresses == null ? Set.of() : addresses;
	}
}
