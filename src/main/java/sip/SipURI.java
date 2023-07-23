package sip;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import util.Serializable;

public interface SipURI extends Serializable {

	String getURIAsString();

	default void serialize(ByteBuffer dest) {
		dest.put(getURIAsString().getBytes(StandardCharsets.UTF_8));
	}

	default int getSize() {
		return getURIAsString().length();
	}

	static SipURI parse(String charSequence) {
		if (FullSipURI.isSipURI(charSequence)) {
			return FullSipURI.parse(charSequence);
		}
		return new SIPAbsoluteURI(charSequence);
	}

	SipURI toCanonicalForm();

	SipURI addParam(String paramName, String value);
}
