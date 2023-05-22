package http.domain;

import util.Serializable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record HTTPResponseLine(HTTPVersion httpVersion, int statusCode, String reasonPhrase) implements Serializable {
	private static final byte CARRIAGE_RETURN = '\r';
	private static final byte LINE_FEED = '\n';
	private static final byte SPACE = ' ';
	private static final int NUM_SPACES = 2;
	private static final int CLRF_LENGTH = 2;
	public static final int STATUS_CODE_LENGTH = 3;

	@Override
	public void serialize(ByteBuffer dest) {
		httpVersion.serialize(dest);
		dest.put(SPACE);
		dest.put((byte) (statusCode / 100 + '0'));
		dest.put((byte) ((statusCode / 10) % 10 + '0'));
		dest.put((byte) (statusCode % 10 + '0'));
		dest.put(SPACE);
		dest.put(reasonPhrase.getBytes(StandardCharsets.UTF_8));
		dest.put(CARRIAGE_RETURN);
		dest.put(LINE_FEED);
	}

	@Override
	public int getSize() {
		return httpVersion.getSize() + STATUS_CODE_LENGTH + reasonPhrase.length() + CLRF_LENGTH + NUM_SPACES;
	}
}
