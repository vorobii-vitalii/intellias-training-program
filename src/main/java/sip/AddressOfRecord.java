package sip;

import static sip.ParseUtils.NOT_FOUND;
import static sip.ParseUtils.findFromFromBegging;
import static sip.ParseUtils.trim;
import static sip.SipParseUtils.parseParameters;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

import util.Serializable;

public record AddressOfRecord(@Nonnull String name, @Nonnull SipURI sipURI, @Nonnull Map<String, String> parameters) implements Serializable {
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

	public Optional<String> getParameterValue(String parameterName) {
		return Optional.ofNullable(parameters().get(parameterName));
	}

	public AddressOfRecord toCanonicalForm() {
		return new AddressOfRecord("", sipURI.toCanonicalForm(), Map.of());
	}

	public AddressOfRecord removeUnsetParameters() {
		return this;
//		var newParameters = new HashMap<String, String>();
//		for (var e : parameters.entrySet()) {
//			if (!e.getValue().isEmpty()) {
//				newParameters.put(e.getKey(), e.getValue());
//			}
//		}
//		return new AddressOfRecord(name, sipURI, newParameters);
	}

	public AddressOfRecord addParam(String param, String value) {
		var newParameters = new HashMap<>(parameters);
		newParameters.put(param, value);
		return new AddressOfRecord(name, sipURI, newParameters);
	}

	public static AddressOfRecord parse(String charSequence) {
		var str = charSequence.trim();
		var laquotIndex = findFromFromBegging(str, LAQUOT);
		// addr-spec case
		if (laquotIndex == NOT_FOUND) {
			return new AddressOfRecord("", SipURI.parse(str), Map.of());
		}
		var raquotIndex = findFromFromBegging(str, RAQUOT);
		var displayName = Optional.ofNullable(trim(str, 0, laquotIndex - 1, CHARACTERS_TO_EXCLUDE))
				.filter(s -> !s.isEmpty())
				.map(s -> isQuoted(s) ? s.substring(1, s.length() - 1) : s)
				.orElse("");
		var sipURI = SipURI.parse(str.subSequence(laquotIndex + 1, raquotIndex).toString());
		return new AddressOfRecord(displayName, sipURI,
				parseParameters(str.subSequence(raquotIndex + 1, str.length()).toString(), PARAMETERS_DELIMITER));
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
		if (!name.isEmpty()) {
			dest.put((byte) NAME_DELIMITER);
			dest.put(name.getBytes(StandardCharsets.UTF_8));
			dest.put((byte) NAME_DELIMITER);
			dest.put(SPACE);
		}
		dest.put((byte) LAQUOT);
		sipURI.serialize(dest);
		dest.put((byte) RAQUOT);
		for (var entry : parameters.entrySet()) {
			if (entry.getValue().isEmpty()) {
				continue;
			}
			dest.put(PARAMETERS_DELIMITER_CHAR);
			dest.put(entry.getKey().getBytes(StandardCharsets.UTF_8));
			dest.put((byte) PARAMETER_DELIMITER);
			dest.put(entry.getValue().getBytes(StandardCharsets.UTF_8));
		}
	}

	@Override
	public int getSize() {
		var totalSize = SIP_URI_DELIMITER_LENGTH
				+ sipURI.getSize()
				+ SIP_URI_DELIMITER_LENGTH
				+ getParametersInBytes();
		if (!name.isEmpty()) {
			totalSize += NAME_DELIMITER_LENGTH
					+ name.length()
					+ NAME_DELIMITER_LENGTH
					+ AOR_DELIMITER_LENGTH;
		}
		return totalSize;
	}

	private int getParametersInBytes() {
		int total = 0;
		for (var entry : parameters.entrySet()) {
			if (entry.getValue().isEmpty()) {
				continue;
			}
			total += PARAMETER_LIST_DELIMITER_LENGTH;
			total += entry.getKey().length();
			total += PARAMETER_DELIMITER_LENGTH;
			total += entry.getValue().length();
		}
		return total;
	}

}
