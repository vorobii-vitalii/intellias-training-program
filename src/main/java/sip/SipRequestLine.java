package sip;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import tcp.server.reader.exception.ParseException;
import util.Serializable;

public record SipRequestLine(String method, SipURI requestURI, SipVersion version) implements Serializable {
	private static final String LWS = "\\s+";
	private static final int CRLF_LENGTH = 2;
	private static final int SPACE_LENGTH = 1;
	private static final byte SPACE = (byte) ' ';
	private static final byte CR = '\r';
	private static final byte NEW_LINE = '\n';

	public static SipRequestLine parse(String str) {
		var arr = str.trim().split(LWS);
		if (arr.length != 3) {
			throw new ParseException("request-line   = method LWS request-target LWS SIP-version CRLF " + str);
		}
		var method = arr[0].toUpperCase();
		var requestURI = SipURI.parse(arr[1]);
		var httpVersion = SipVersion.parse(arr[2]);
		return new SipRequestLine(method, requestURI, httpVersion);
	}

	@Override
	public void serialize(ByteBuffer dest) {
		dest.put(method.getBytes(StandardCharsets.UTF_8));
		dest.put(SPACE);
		requestURI.serialize(dest);
		dest.put(SPACE);
		version.serialize(dest);
		dest.put(CR);
		dest.put(NEW_LINE);
	}

	@Override
	public int getSize() {
		return method.length() + SPACE_LENGTH + requestURI.getSize() + SPACE_LENGTH + version.getSize() + CRLF_LENGTH;
	}
}
