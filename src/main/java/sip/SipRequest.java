package sip;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import util.Serializable;

public record SipRequest(SipRequestLine requestLine, SipRequestHeaders headers, byte[] payload)
		implements Serializable, SipMessage, Cloneable<SipRequest> {

	@Override
	public SipRequest replicate() {
		return new SipRequest(
				requestLine,
				headers.replicate(),
				payload
		);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		SipRequest that = (SipRequest) o;
		return Objects.equals(requestLine, that.requestLine) && Objects.equals(headers, that.headers) && Arrays.equals(
				payload, that.payload);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(requestLine, headers);
		result = 31 * result + Arrays.hashCode(payload);
		return result;
	}

	@Override
	public String toString() {
		return "SipRequest{" +
				"requestLine=" + requestLine +
				", headers=" + headers +
				", payload=" + Arrays.toString(payload) +
				'}';
	}

	@Override
	public void serialize(ByteBuffer dest) {
		requestLine.serialize(dest);
		headers.serialize(dest);
		dest.put(payload);
	}

	@Override
	public int getSize() {
		return requestLine().getSize() + headers.getSize() + payload.length;
	}
}
