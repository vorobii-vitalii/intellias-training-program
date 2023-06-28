package sip;

public record SipSentProtocol(String protocolName, String protocolVersion, String transportName) {

	public static SipSentProtocol parse(String str) {
		var arr = str.split("/");
		if (arr.length != 3) {
			throw new IllegalArgumentException("Invalid SIP sent protocol: protocol-name SLASH protocol-version SLASH transport");
		}
		return new SipSentProtocol(arr[0].trim(), arr[1].trim(), arr[2].trim());
	}

}
