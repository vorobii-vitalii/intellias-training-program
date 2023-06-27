package sip;

import tcp.server.reader.exception.ParseException;

public record SipRequestLine(String method, SIPRequestURI requestURI, SipVersion version) {
	private static final byte SPACE = ' ';

	public static SipRequestLine parse(CharSequence charSequence) {
		var n = charSequence.length();
		var leftSpace = -1;
		for (var i = 0; i < n; i++) {
			if (charSequence.charAt(i) == SPACE) {
				leftSpace = i;
				break;
			}
		}
		int rightSpace = -1;
		for (int i = n - 1; i >= 0; i--) {
			if (charSequence.charAt(i) == SPACE) {
				rightSpace = i;
				break;
			}
		}
		if (leftSpace == -1 || rightSpace == -1 || leftSpace == rightSpace) {
			throw new ParseException("request-line   = method SP request-target SP HTTP-version CRLF " + charSequence);
		}
		var method = charSequence.subSequence(0, leftSpace).toString();
		var httpVersion = SipVersion.parse(charSequence.subSequence(rightSpace + 1, n));
		var requestURI = calcSIPREquestURI(charSequence.subSequence(leftSpace + 1, rightSpace).toString());
		return new SipRequestLine(method, requestURI, httpVersion);
	}

	private static SIPRequestURI calcSIPREquestURI(String str) {
		if (SipURI.isSipURI(str)) {
			return SipURI.parse(str);
		}
		return new SIPAbsoluteURI(str);
	}

}
