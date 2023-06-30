package sip;

import static sip.ParseUtils.NOT_FOUND;
import static sip.ParseUtils.findFromFromBegging;
import static sip.ParseUtils.trim;
import static sip.SipParseUtils.parseParameters;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

import util.Serializable;

public record AddressOfRecord(@Nonnull String name, @Nonnull SipURI sipURI, @Nonnull Map<String, String> parameters) implements Serializable {
	private static final String FALLBACK_NAME = "Anonymous";
	private static final char LAQUOT = '<';
	private static final char RAQUOT = '>';
	private static final int NAME_DELIMITER_LENGTH = 1;
	private static final int AOR_DELIMITER_LENGTH = 1;
	private static final int SIP_URI_DELIMITER_LENGTH = 1;
	private static final Set<Character> CHARACTERS_TO_EXCLUDE = Set.of(' ', '\n');
	public static final String PARAMETERS_DELIMITER = ";";
	public static final char NAME_DELIMITER = '"';
	private static final Set<Character> QUOTE_CHARACTERS = Set.of('\'', NAME_DELIMITER);
	public static final int PARAMETER_LIST_DELIMITER_LENGTH = 1;
	public static final int PARAMETER_DELIMITER_LENGTH = 1;
	public static final byte SPACE = (byte) ' ';
	public static final byte PARAMETERS_DELIMITER_CHAR = (byte) ';';
	public static final char PARAMETER_DELIMITER = '=';

	public AddressOfRecord addParam(String param, String value) {
		var newParameters = new HashMap<>(parameters);
		newParameters.put(param, value);
		return new AddressOfRecord(name, sipURI, newParameters);
	}

	public static AddressOfRecord parse(String charSequence) {
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
		var sipURI = SipURI.parse(charSequence.subSequence(laquotIndex + 1, raquotIndex).toString());
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

	@Override
	public void serialize(ByteBuffer dest) {
		dest.put((byte) NAME_DELIMITER);
		dest.put(name.getBytes(StandardCharsets.UTF_8));
		dest.put((byte) NAME_DELIMITER);
		dest.put(SPACE);
		dest.put((byte) LAQUOT);
		sipURI.serialize(dest);
		dest.put((byte) RAQUOT);
		for (var entry : parameters.entrySet()) {
			dest.put(PARAMETERS_DELIMITER_CHAR);
			dest.put(entry.getKey().getBytes(StandardCharsets.UTF_8));
			dest.put((byte) PARAMETER_DELIMITER);
			dest.put(entry.getValue().getBytes(StandardCharsets.UTF_8));
		}
	}

	@Override
	public int getSize() {
		return NAME_DELIMITER_LENGTH
				+ name.length()
				+ NAME_DELIMITER_LENGTH
				+ AOR_DELIMITER_LENGTH
				+ SIP_URI_DELIMITER_LENGTH
				+ sipURI.getSize()
				+ SIP_URI_DELIMITER_LENGTH
				+ getParametersInBytes();
	}

	private int getParametersInBytes() {
		int total = 0;
		for (var entry : parameters.entrySet()) {
			total += PARAMETER_LIST_DELIMITER_LENGTH;
			total += entry.getKey().length();
			total += PARAMETER_DELIMITER_LENGTH;
			total += entry.getValue().length();
		}
		return total;
	}

}
