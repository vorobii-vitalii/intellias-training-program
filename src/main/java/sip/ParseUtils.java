package sip;

import java.util.Set;

public final class ParseUtils {
	public static final int NOT_FOUND = -1;

	public static String trim(CharSequence sequence, int fromInclusive, int endInclusive, Set<Character> charactersToExclude) {
		while (fromInclusive <= endInclusive && charactersToExclude.contains(sequence.charAt(fromInclusive))) {
			fromInclusive++;
		}
		while (fromInclusive <= endInclusive && charactersToExclude.contains(sequence.charAt(endInclusive))) {
			endInclusive--;
		}
		if (fromInclusive > endInclusive) {
			return null;
		}
		return sequence.subSequence(fromInclusive, endInclusive + 1).toString();
	}

	public static int findFromFromBegging(CharSequence charSequence, char b) {
		var n = charSequence.length();
		for (int i = 0; i < n; i++) {
			if (charSequence.charAt(i) == b) {
				return i;
			}
		}
		return NOT_FOUND;
	}

	public static int findFromFromEnd(CharSequence charSequence, char b) {
		var n = charSequence.length();
		for (int i = n - 1; i >= 0; i--) {
			if (charSequence.charAt(i) == b) {
				return i;
			}
		}
		return NOT_FOUND;
	}

}
