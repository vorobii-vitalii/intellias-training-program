package sip;

import java.util.regex.Pattern;

import tcp.server.reader.exception.ParseException;

public record SipVersion(int majorVersion, int minorVersion) {
	private static final Pattern SIP_VERSION_PATTERN = Pattern.compile("SIP/(\\d+)\\.(\\d+)", Pattern.CASE_INSENSITIVE);

	public static SipVersion parse(CharSequence charSequence) {
		var matcher = SIP_VERSION_PATTERN.matcher(charSequence);
		if (!matcher.matches()) {
			throw new ParseException("SIP-Version    =  \"SIP\" \"/\" 1*DIGIT \".\" 1*DIGIT");
		}
		return new SipVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
	}

}
