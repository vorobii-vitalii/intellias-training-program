package sip;

import java.util.Arrays;
import java.util.Objects;

public record SipRequest(SipRequestLine requestLine, SipRequestHeaders headers, byte[] payload) {

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
}
