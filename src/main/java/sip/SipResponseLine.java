package sip;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import util.Serializable;

//Response          =  Status-Line
//		*( message-header )
//		CRLF
//		[ message-body ]
//
//		Status-Line     =  SIP-Version SP Status-Code SP Reason-Phrase CRLF
public record SipResponseLine(SipVersion version, SipStatusCode sipStatusCode, String reasonPhrase) implements Serializable {
	private static final int CRLF_LENGTH = 2;
	private static final int SPACE_LENGTH = 1;
	private static final byte SPACE = (byte) ' ';
	private static final byte CR = '\r';
	private static final byte NEW_LINE = '\n';

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
