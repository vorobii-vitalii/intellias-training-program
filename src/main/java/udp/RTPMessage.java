package udp;

import java.util.Arrays;
import java.util.Objects;

public class RTPMessage {
	int version;
	boolean isPadded;
	boolean isExtensionEnabled;
	boolean isMarker;
	int payloadType;
	int sequenceNumber;
	int timestamp;
	int synchronizationSource;
	int[] contributionSourceIdentifiers;
	byte[] payload;

	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}

	public boolean isPadded() {
		return isPadded;
	}

	public void setPadded(boolean padded) {
		isPadded = padded;
	}

	public boolean isExtensionEnabled() {
		return isExtensionEnabled;
	}

	public void setExtensionEnabled(boolean extensionEnabled) {
		isExtensionEnabled = extensionEnabled;
	}

	public boolean isMarker() {
		return isMarker;
	}

	public void setMarker(boolean marker) {
		isMarker = marker;
	}

	public int getPayloadType() {
		return payloadType;
	}

	public void setPayloadType(int payloadType) {
		this.payloadType = payloadType;
	}

	public int getSequenceNumber() {
		return sequenceNumber;
	}

	public void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}

	public int getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(int timestamp) {
		this.timestamp = timestamp;
	}

	public int getSynchronizationSource() {
		return synchronizationSource;
	}

	public void setSynchronizationSource(int synchronizationSource) {
		this.synchronizationSource = synchronizationSource;
	}

	public int[] getContributionSourceIdentifiers() {
		return contributionSourceIdentifiers;
	}

	public void setContributionSourceIdentifiers(int[] contributionSourceIdentifiers) {
		this.contributionSourceIdentifiers = contributionSourceIdentifiers;
	}

	public byte[] getPayload() {
		return payload;
	}

	public void setPayload(byte[] payload) {
		this.payload = payload;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		RTPMessage that = (RTPMessage) o;
		return version == that.version && isPadded == that.isPadded && isExtensionEnabled == that.isExtensionEnabled && isMarker == that.isMarker
				&& payloadType == that.payloadType && sequenceNumber == that.sequenceNumber && timestamp == that.timestamp
				&& synchronizationSource == that.synchronizationSource && Arrays.equals(contributionSourceIdentifiers,
				that.contributionSourceIdentifiers) && Arrays.equals(payload, that.payload);
	}

	@Override
	public int hashCode() {
		int result =
				Objects.hash(version, isPadded, isExtensionEnabled, isMarker, payloadType, sequenceNumber, timestamp, synchronizationSource);
		result = 31 * result + Arrays.hashCode(contributionSourceIdentifiers);
		result = 31 * result + Arrays.hashCode(payload);
		return result;
	}

	@Override
	public String toString() {
		return "RTPMessage{" +
				"version=" + version +
				", isPadded=" + isPadded +
				", isExtensionEnabled=" + isExtensionEnabled +
				", isMarker=" + isMarker +
				", payloadType=" + payloadType +
				", sequenceNumber=" + sequenceNumber +
				", timestamp=" + timestamp +
				", synchronizationSource=" + synchronizationSource +
				", contributionSourceIdentifiers=" + Arrays.toString(contributionSourceIdentifiers) +
				", payload=" + Arrays.toString(payload) +
				'}';
	}
}
