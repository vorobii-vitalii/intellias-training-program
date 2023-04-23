package util;

import tcp.server.BufferContext;

import java.util.ArrayList;
import java.util.List;

public final class ByteUtils {
	private static final int NOT_FOUND = -1;

	private ByteUtils() {
		// Utility classes should not be instantiated
	}

	public static void applyMaskingKey(byte[] arr, byte[] mask) {
		for (var i = 0; i < arr.length; i++) {
			arr[i] = (byte) (arr[i] ^ mask[i % mask.length]);
		}
	}

	public interface SubsequenceExtractor<T> {
		void init(int size);
		void append(byte b);

		T getResult();
	}

	public static int getIndexOfRepetitiveSubsequence(List<Integer> subsequenceMatchIndexes, int subsequenceSize) {
		for (var i = 0; i < subsequenceMatchIndexes.size() - 1; i++) {
			if (subsequenceMatchIndexes.get(i + 1) == subsequenceMatchIndexes.get(i) + subsequenceSize) {
				return i + 1;
			}
		}
		return NOT_FOUND;
	}

	public static <T> T extractSubsequence(
					BufferContext context,
					List<Integer> subsequenceMatchIndexes,
					int subsequenceSize,
					int subsequenceNum,
					SubsequenceExtractor<T> subsequenceExtractor
	) {
		int start = subsequenceNum == 0 ? 0 : subsequenceMatchIndexes.get(subsequenceNum - 1) + subsequenceSize;
		int end = subsequenceMatchIndexes.get(subsequenceNum);
		subsequenceExtractor.init(end - start + 1);
		for (int i = start; i < end; i++) {
			subsequenceExtractor.append(context.get(i));
		}
		return subsequenceExtractor.getResult();
	}

	public static List<Integer> findAllMatches(BufferContext bufferContext, byte[] sequence) {
		var matches = new ArrayList<Integer>();
		var bufferContextSize = bufferContext.size();
		for (var i = 0; i < bufferContextSize - sequence.length + 1; i++) {
			var matched = true;
			for (var j = 0; j < sequence.length; j++) {
				if (bufferContext.get(i + j) != sequence[j]) {
					matched = false;
					break;
				}
			}
			if (matched) {
				matches.add(i);
			}
		}
		return matches;
	}

}
