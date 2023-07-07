package sip;

public class SipRequestBuilder implements SipMessageBuilder {

	private final SipRequestLine sipRequestLine;
	private final SipRequestHeaders sipRequestHeaders = new SipRequestHeaders();
	private byte[] body;

	public SipRequestBuilder(SipRequestLine sipRequestLine) {
		this.sipRequestLine = sipRequestLine;
	}

	@Override
	public SipMessageBuilder setHeader(String headerName, String headerValue) {
		sipRequestHeaders.addSingleHeader(headerName, headerValue);
		return this;
	}

	@Override
	public SipMessageBuilder setBody(byte[] body) {
		sipRequestHeaders.setContentLength(body.length);
		this.body = body;
		return this;
	}

	@Override
	public SipMessage build() {
		return new SipRequest(sipRequestLine, sipRequestHeaders, body);
	}

	@Override
	public int getContentLength() {
		return sipRequestHeaders.getContentLength();
	}
}
