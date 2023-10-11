package sip.request_handling.media;

import java.util.Collection;

import reactor.util.annotation.NonNull;
import sip.Address;

public interface MediaMappingStorage {
	void addMapping(Address source, Address destination, String mediaType, String sessionId);
	void invalidate(String sessionId);
	@NonNull
	Collection<Address> getReceivers(Address source, String mediaType);
}
