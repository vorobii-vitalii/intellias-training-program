package sip;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import tcp.server.reader.exception.ParseException;
import util.Serializable;

//Response          =  Status-Line
//		*( message-header )
//		CRLF
//		[ message-payload ]
//
//		Status-Line     =  SIP-Version SP Status-Code SP Reason-Phrase CRLF
public record SipResponseLine(SipVersion version, SipStatusCode sipStatusCode, String reasonPhrase) implements Serializable {
	private static final String LWS = "\\s+";
	private static final int CRLF_LENGTH = 2;
	private static final int SPACE_LENGTH = 1;
	private static final byte SPACE = (byte) ' ';
	private static final byte CR = '\r';
	private static final byte NEW_LINE = '\n';

	public static SipResponseLine parse(String str) {
		var arr = str.trim().split(LWS, 3);
		if (arr.length != 3) {
			throw new ParseException("Status-Line     =  SIP-Version SP Status-Code SP Reason-Phrase CRLF" + str);
		}
		var sipVersion = SipVersion.parse(arr[0]);
		var statusCode = new SipStatusCode(Integer.parseInt(arr[1]));
		var reasonMessage = arr[2].trim();
		return new SipResponseLine(sipVersion, statusCode, reasonMessage);
	}

	public static boolean isSipResponseLine(String str) {
		var arr = str.trim().split(LWS);
		return SipVersion.matches(arr[0]);
	}

	@Override
	public void serialize(ByteBuffer dest) {
		version.serialize(dest);
		dest.put(SPACE);
		sipStatusCode.serialize(dest);
		dest.put(SPACE);
		dest.put(reasonPhrase.getBytes(StandardCharsets.UTF_8));
		dest.put(CR);
		dest.put(NEW_LINE);
	}

	@Override
	public int getSize() {
		return version.getSize()
				+ SPACE_LENGTH
				+ sipStatusCode.getSize()
				+ SPACE_LENGTH
				+ reasonPhrase.length()
				+ CRLF_LENGTH;
	}

}
