package sip;

public interface SipMessageBuilder {
	SipMessageBuilder setHeader(String headerName, String headerValue);
	SipMessageBuilder setBody(byte[] body);
	SipMessage build();
	int getContentLength();
}
