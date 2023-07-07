package sip;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import tcp.server.reader.exception.ParseException;
import util.Serializable;

public record SipVersion(int majorVersion, int minorVersion) implements Serializable {
	private static final Pattern SIP_VERSION_PATTERN = Pattern.compile("SIP/(\\d+)\\.(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final int SIP_VERSION_LENGTH = 7;
	private static final byte[] SIP_VERSION_PREFIX = "SIP/".getBytes(StandardCharsets.UTF_8);
	private static final byte DOT = (byte) '.';

	public static boolean matches(CharSequence charSequence) {
		return SIP_VERSION_PATTERN.matcher(charSequence).matches();
	}

	public static SipVersion parse(CharSequence charSequence) {
		var matcher = SIP_VERSION_PATTERN.matcher(charSequence);
		if (!matcher.matches()) {
			throw new ParseException("SIP-Version    =  \"SIP\" \"/\" 1*DIGIT \".\" 1*DIGIT");
		}
		return new SipVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
	}

	@Override
	public void serialize(ByteBuffer dest) {
		dest.put(SIP_VERSION_PREFIX);
		dest.put((byte) (majorVersion + '0'));
		dest.put(DOT);
		dest.put((byte) (minorVersion + '0'));
	}

	@Override
	public int getSize() {
		return SIP_VERSION_LENGTH;
	}
}
