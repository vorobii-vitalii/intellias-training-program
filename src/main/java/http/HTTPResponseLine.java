package http;

import util.Serializable;

import java.nio.charset.StandardCharsets;

public record HTTPResponseLine(HTTPVersion httpVersion, int statusCode, String reasonPhrase) implements Serializable {
	private static final byte CARRIAGE_RETURN = '\r';
	private static final byte LINE_FEED = '\n';
	private static final byte SPACE = ' ';
	private static final int NUM_SPACES = 2;
	private static final int CLRF_LENGTH = 2;

	@Override
	public byte[] serialize() {
		byte[] httpVersionBytes = httpVersion.serialize();
		byte[] messageBytes = reasonPhrase.getBytes(StandardCharsets.UTF_8);
		byte[] statusBytes = serializeStatusCode(statusCode);
		byte[] responseArr = new byte[
						httpVersionBytes.length
										+ messageBytes.length
										+ statusBytes.length
										+ NUM_SPACES
										+ CLRF_LENGTH
						];
		System.arraycopy(httpVersionBytes,
						0,
						responseArr,
						0,
						httpVersionBytes.length
		);
		responseArr[httpVersionBytes.length] = SPACE;
		System.arraycopy(statusBytes,
						0,
						responseArr,
						httpVersionBytes.length + 1,
						statusBytes.length
		);
		responseArr[httpVersionBytes.length + 1 + statusBytes.length] = SPACE;
		System.arraycopy(messageBytes,
						0,
						responseArr,
						httpVersionBytes.length + 2 + statusBytes.length,
						messageBytes.length
		);
		responseArr[responseArr.length - 2] = CARRIAGE_RETURN;
		responseArr[responseArr.length - 1] = LINE_FEED;
		return responseArr;
	}

	public static byte[] serializeStatusCode(int value) {
		return new byte[] {
						(byte) (value / 100 + '0'),
						(byte) ((value / 10) % 10 + '0'),
						(byte) (value % 10 + '0')
		};
	}

}
