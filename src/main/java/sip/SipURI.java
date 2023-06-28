package sip;

public sealed interface SipURI permits FullSipURI, SIPAbsoluteURI  {
	static SipURI parse(CharSequence charSequence) {
		if (FullSipURI.isSipURI(charSequence)) {
			return FullSipURI.parse(charSequence);
		}
		return new SIPAbsoluteURI(charSequence);
	}

}
