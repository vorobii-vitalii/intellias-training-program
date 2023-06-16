package util;

public final class WebSocketUtils {
	private WebSocketUtils() {
		// Utility classes should not be instantiated
	}

	public static void applyMaskingKey(byte[] arr, byte[] mask) {
		for (var i = 0; i < arr.length; i++) {
			arr[i] = (byte) (arr[i] ^ mask[i % mask.length]);
		}
	}

}
