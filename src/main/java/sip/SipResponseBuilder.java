package sip;

public class SipResponseBuilder implements SipMessageBuilder {
	private final SipResponseHeaders sipResponseHeaders = new SipResponseHeaders();
	private byte[] payload;
	private final SipResponseLine sipResponseLine;

	public SipResponseBuilder(SipResponseLine sipResponseLine) {
		this.sipResponseLine = sipResponseLine;
	}


	@Override
	public SipMessageBuilder setHeader(String headerName, String headerValue) {
		sipResponseHeaders.addHeader(headerName, headerValue);
		return this;
	}

	@Override
	public SipMessageBuilder setBody(byte[] body) {
		this.payload = body;
		sipResponseHeaders.setContentLength(body.length);
		return this;
	}

	@Override
	public SipMessage build() {
		return new SipResponse(sipResponseLine, sipResponseHeaders, payload);
	}

	@Override
	public int getContentLength() {
		return sipResponseHeaders.getContentLength();
	}
}
