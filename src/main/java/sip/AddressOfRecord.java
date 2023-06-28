package sip;

import static sip.ParseUtils.NOT_FOUND;
import static sip.ParseUtils.findFromFromBegging;
import static sip.ParseUtils.trim;
import static sip.SipParseUtils.parseParameters;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record AddressOfRecord(String name, SipURI sipURI, Map<String, String> parameters) {
	private static final String FALLBACK_NAME = "Anonymous";
	private static final char LAQUOT = '<';
	private static final char RAQUOT = '>';
	private static final Set<Character> CHARACTERS_TO_EXCLUDE = Set.of(' ', '\n');
	public static final String PARAMETERS_DELIMITER = ";";
	private static final Set<Character> QUOTE_CHARACTERS = Set.of('\'', '"');

	public static AddressOfRecord parse(CharSequence charSequence) {
		var laquotIndex = findFromFromBegging(charSequence, LAQUOT);
		// addr-spec case
		if (laquotIndex == NOT_FOUND) {
			return new AddressOfRecord(FALLBACK_NAME, SipURI.parse(charSequence), Map.of());
		}
		var raquotIndex = findFromFromBegging(charSequence, RAQUOT);
		var displayName = Optional.ofNullable(trim(charSequence, 0, laquotIndex - 1, CHARACTERS_TO_EXCLUDE))
				.filter(s -> !s.isEmpty())
				.map(s -> isQuoted(s) ? s.substring(1, s.length() - 1) : s)
				.orElse(FALLBACK_NAME);
		var sipURI = SipURI.parse(charSequence.subSequence(laquotIndex + 1, raquotIndex));
		return new AddressOfRecord(displayName, sipURI,
				parseParameters(charSequence.subSequence(raquotIndex + 1, charSequence.length()).toString(), PARAMETERS_DELIMITER));
	}

	private static boolean isQuoted(String str) {
		if (str.isEmpty()) {
			return false;
		}
		var firstChar = str.charAt(0);
		if (firstChar != str.charAt(str.length() - 1)) {
			return false;
		}
		return QUOTE_CHARACTERS.contains(firstChar);
	}
}
