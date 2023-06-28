package sip;

import tcp.server.reader.exception.ParseException;

public record SipRequestLine(String method, SipURI requestURI, SipVersion version) {
	private static final String LWS = "\\s+";

	public static SipRequestLine parse(String str) {
		var arr = str.trim().split(LWS);
		if (arr.length != 3) {
			throw new ParseException("request-line   = method LWS request-target LWS SIP-version CRLF " + str);
		}
		var method = arr[0];
		var httpVersion = SipVersion.parse(arr[1]);
		var requestURI = SipURI.parse(arr[2]);
		return new SipRequestLine(method, requestURI, httpVersion);
	}

}
