package sip;

import java.nio.ByteBuffer;

import util.Serializable;

public record SipResponse(SipResponseLine responseLine, SipResponseHeaders headers, byte[] body) implements Serializable {
	@Override
	public void serialize(ByteBuffer dest) {
		responseLine.serialize(dest);
		headers.serialize(dest);
		dest.put(body);
	}

	@Override
	public int getSize() {
		return responseLine.getSize() + headers.getSize() + body.length;
	}
}
