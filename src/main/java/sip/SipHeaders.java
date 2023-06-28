package sip;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;

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
	private final List<Via> viaList = new ArrayList<>();
	private AddressOfRecord from;
	private AddressOfRecord to;
	private AddressOfRecord referTo;
	private CommandSequence commandSequence;
	private Integer maxForwards;
	private int contentLength = 0;
	private ContactList contactList;
	private SipMediaType contentType;

	private final Map<String, Consumer<String>> headerSetterByHeaderName = Map.of(
					"from", v -> this.from = AddressOfRecord.parse(v),
					"to", v -> this.to = AddressOfRecord.parse(v),
					"refer-to", v -> this.referTo = AddressOfRecord.parse(v),
					"cseq", v -> this.commandSequence = CommandSequence.parse(v),
					"via", v -> viaList.addAll(Via.parseMultiple(v)),
					"content-length", v -> contentLength = Integer.parseInt(v.trim()),
					"max-forwards", v -> maxForwards = Integer.parseInt(v.trim()),
					"contact", v -> contactList = ContactList.parse(v),
					"content-type", v -> contentType = SipMediaType.parse(v)
	);

	public void addSingleHeader(String headerName, String value) {
		var lowerCasedHeader = headerName.toLowerCase();
		var formattedName = Optional.ofNullable(COMPACT_HEADERS_MAP.get(lowerCasedHeader)).orElse(lowerCasedHeader);
		var headerSetter = headerSetterByHeaderName.get(formattedName);
		if (headerSetter != null) {
			headerSetter.accept(value);
		} else {
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

	public void setFrom(AddressOfRecord from) {
		this.from = from;
	}

	public AddressOfRecord getTo() {
		return to;
	}

	public void setTo(AddressOfRecord to) {
		this.to = to;
	}

	public AddressOfRecord getReferTo() {
		return referTo;
	}

	public void setReferTo(AddressOfRecord referTo) {
		this.referTo = referTo;
	}

	public CommandSequence getCommandSequence() {
		return commandSequence;
	}

	public void setCommandSequence(CommandSequence commandSequence) {
		this.commandSequence = commandSequence;
	}

	public ContactList getContactList() {
		return contactList;
	}

	public void setContactList(ContactList contactList) {
		this.contactList = contactList;
	}

	@Nonnull
	public List<Via> getViaList() {
		return viaList;
	}

	public int getContentLength() {
		return contentLength;
	}

	public void setContentLength(int contentLength) {
		this.contentLength = contentLength;
	}

	public Integer getMaxForwards() {
		return maxForwards;
	}

	public void setMaxForwards(Integer maxForwards) {
		this.maxForwards = maxForwards;
	}

	public SipMediaType getContentType() {
		return contentType;
	}

	public void setContentType(SipMediaType contentType) {
		this.contentType = contentType;
	}

	public void addVia(Via via) {
		viaList.add(via);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SipHeaders that = (SipHeaders) o;
		return contentLength == that.contentLength && Objects.equals(headerMap, that.headerMap) && Objects.equals(from, that.from) && Objects.equals(to, that.to) && Objects.equals(referTo, that.referTo) && Objects.equals(commandSequence, that.commandSequence) && Objects.equals(maxForwards, that.maxForwards) && Objects.equals(viaList, that.viaList) && Objects.equals(contactList, that.contactList) && Objects.equals(contentType, that.contentType) && Objects.equals(headerSetterByHeaderName, that.headerSetterByHeaderName);
	}

	@Override
	public int hashCode() {
		return Objects.hash(headerMap, from, to, referTo, commandSequence, maxForwards, contentLength, viaList, contactList, contentType, headerSetterByHeaderName);
	}
}
