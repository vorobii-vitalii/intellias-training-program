package sip;

public record SIPAbsoluteURI(String uri) implements SipURI {
	@Override
	public String getURIAsString() {
		return uri;
	}

	@Override
	public SipURI toCanonicalForm() {
		return this;
	}

	@Override
	public SipURI addParam(String paramName, String value) {
		throw new IllegalStateException("");
	}
}
