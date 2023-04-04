package util;

import tcp.server.ReadBufferContext;

import java.util.ArrayList;
import java.util.List;

public final class Utils {

	private Utils() {
		// Utility classes should not be instantiated
	}

	public static List<Integer> findAllMatches(ReadBufferContext bufferContext, byte[] sequence) {
		var matches = new ArrayList<Integer>();
		var N = bufferContext.size();
		for (var i = 0; i < N - sequence.length + 1; i++) {
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
