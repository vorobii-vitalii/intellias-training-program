package sip.request_handling;

import java.util.Arrays;
import java.util.Objects;

import sip.SipRequestHeaders;

public record DialogRequest(String callId, String methodName, SipRequestHeaders overrideHeaders, byte[] body) {

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		DialogRequest that = (DialogRequest) o;
		return Objects.equals(callId, that.callId)
				&& Objects.equals(methodName, that.methodName)
				&& Objects.equals(overrideHeaders, that.overrideHeaders)
				&& Arrays.equals(body, that.body);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(callId, methodName, overrideHeaders);
		result = 31 * result + Arrays.hashCode(body);
		return result;
	}
}
