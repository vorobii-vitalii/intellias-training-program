package sip;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import util.Serializable;

public record Address(String host, Integer port) implements Serializable {
	private static final byte COLON = (byte) ':';

	public static Address parse(String str) {
		var arr = str.trim().split(":");
		var host = arr[0].trim();
		return new Address(host, arr.length > 1 ? Integer.parseInt(arr[1]) : null);
	}

	@Override
	public void serialize(ByteBuffer dest) {
		dest.put(host.getBytes(StandardCharsets.UTF_8));
		if (port != null) {
			dest.put(COLON);
			dest.put(String.valueOf(port).getBytes(StandardCharsets.UTF_8));
		}
	}

	@Override
	public int getSize() {
		return host.length() + (port == null ? 0 : String.valueOf(port).length() + 1);
	}

	public String asString() {
		return host + (port == null ? "" : (":" + port));
	}
}
