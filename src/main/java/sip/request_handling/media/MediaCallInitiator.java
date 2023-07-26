package sip.request_handling.media;

import sip.request_handling.calls.CallDetails;

public class MediaCallInitiator {
	private final MediaMappingStorage mediaMappingStorage;

	public MediaCallInitiator(MediaMappingStorage mediaMappingStorage) {
		this.mediaMappingStorage = mediaMappingStorage;
	}

	public void initiate(CallDetails callDetails) {
		var addressesByMediaAddressType = callDetails.addressesByMediaAddressType();
		var sessionId = callDetails.callId();
		for (var entry : addressesByMediaAddressType.entrySet()) {
			var mediaType = entry.getKey();
			var addresses = entry.getValue();
			for (var sourceAddress : addresses) {
				for (var destination : addresses) {
					if (sourceAddress.equals(destination)) {
						continue;
					}
					mediaMappingStorage.addMapping(sourceAddress, destination, mediaType, sessionId);
				}
			}
		}
	}

}
