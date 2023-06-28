package sip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nonnull;

public class SipHeaders {
	private static final Map<String, String> COMPACT_HEADERS_MAP = Map.of(
			"i", "call-id",
			"m", "contact",
			"e", "contact-encoding",
			"l", "content-length",
			"c", "content-type",
			"f", "from",
			"s", "subject",
			"k", "supported",
			"t", "to",
			"v", "via"
	);

	private final Map<String, List<String>> headerMap = new HashMap<>();
	private AddressOfRecord from;
	private AddressOfRecord to;
	private AddressOfRecord referTo;
	private CommandSequence commandSequence;
	private int contentLength = 0;
	private final List<Via> viaList = new ArrayList<>();

	private final Map<String, Consumer<String>> headerSetterByHeaderName = Map.of(
			"from", v -> this.from = AddressOfRecord.parse(v),
			"to", v -> this.to = AddressOfRecord.parse(v),
			"refer-to", v -> this.referTo = AddressOfRecord.parse(v),
			"cseq", v -> this.commandSequence = CommandSequence.parse(v),
			"via", v -> viaList.addAll(Via.parseMultiple(v)),
			"content-length", v -> contentLength = Integer.parseInt(v.trim())
	);

	public void addSingleHeader(String headerName, String value) {
		var lowerCasedHeader = headerName.toLowerCase();
		var formattedName = Optional.ofNullable(COMPACT_HEADERS_MAP.get(lowerCasedHeader)).orElse(lowerCasedHeader);
		var headerSetter = headerSetterByHeaderName.get(formattedName);
		if (headerSetter != null) {
			headerSetter.accept(value);
		}
		else {
			headerMap.compute(formattedName, (s, strings) -> {
				if (strings == null) {
					strings = new ArrayList<>();
				}
				strings.add(value);
				return strings;
			});
		}
	}

	public Optional<String> getSingleHeaderValue(String headerName) {
		return Optional.ofNullable(headerMap.get(headerName))
				.map(v -> {
					if (v.size() > 1) {
						throw new IllegalStateException("More than 1 value for header = " + headerName);
					}
					return v.get(0);
				});
	}

	public AddressOfRecord getFrom() {
		return from;
	}

	public AddressOfRecord getTo() {
		return to;
	}

	public AddressOfRecord getReferTo() {
		return referTo;
	}

	public CommandSequence getCommandSequence() {
		return commandSequence;
	}

	@Nonnull
	public List<Via> getViaList() {
		return viaList;
	}

	public int getContentLength() {
		return contentLength;
	}
}
