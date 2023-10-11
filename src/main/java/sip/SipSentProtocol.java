package sip;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import util.Serializable;

// sent-protocol     =  protocol-name SLASH protocol-version SLASH transport
public record SipSentProtocol(String protocolName, String protocolVersion, String transportName) implements Serializable {
	private static final int DELIMITER_LENGTH = 1;
	public static final byte SLASH = (byte) '/';

	public static SipSentProtocol parse(String str) {
		var arr = str.split("/");
		if (arr.length != 3) {
			throw new IllegalArgumentException("Invalid SIP sent protocol: protocol-name SLASH protocol-version SLASH transport");
		}
		return new SipSentProtocol(arr[0].trim(), arr[1].trim(), arr[2].trim());
	}

	@Override
	public void serialize(ByteBuffer dest) {
		dest.put(protocolName.getBytes(StandardCharsets.UTF_8));
		dest.put(SLASH);
		dest.put(protocolVersion.getBytes(StandardCharsets.UTF_8));
		dest.put(SLASH);
		dest.put(transportName.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public int getSize() {
		return protocolName.length() + DELIMITER_LENGTH + protocolVersion.length() + DELIMITER_LENGTH + transportName.length();
	}
}
