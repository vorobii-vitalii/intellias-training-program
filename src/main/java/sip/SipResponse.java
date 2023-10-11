package sip;

import java.nio.ByteBuffer;

import document_editor.utils.Copyable;
import util.Serializable;

public record SipResponse(SipResponseLine responseLine, SipResponseHeaders headers, byte[] payload)
		implements Serializable, SipMessage, Cloneable<SipResponse>
{
	@Override
	public void serialize(ByteBuffer dest) {
		headers.setContentLength(payload.length);
		responseLine.serialize(dest);
		headers.serialize(dest);
		dest.put(payload);
	}

	@Override
	public int getSize() {
		return responseLine.getSize() + headers.getSize() + payload.length;
	}

	public boolean isSessionDescriptionProvided() {
		// TODO: Is it sufficient to check Content-Type == application/sdp?
		return payload.length > 0 && headers.getContentType() != null;
	}

	@Override
	public SipResponse replicate() {
		return new SipResponse(
				responseLine,
				headers.replicate(),
				payload
		);
	}
}
