package sip;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import util.Serializable;

public class SipResponseHeaders implements Serializable {
	private static final String FROM = "from";
	private static final String TO = "to";
	private static final String REFER_TO = "refer-to";
	private static final String COMMAND_SEQUENCE = "cseq";
	private static final String VIA = "via";
	private static final String CONTENT_LENGTH = "content-length";
	private static final String MAX_FORWARDS = "max-forwards";
	private static final String CONTACT = "contact";
	private static final String CONTENT_TYPE = "content-type";
	public static final int CRLF_LENGTH = 2;
	public static final int COLON_LENGTH = 1;
	public static final char COLON = ':';
	public static final char CARRET = '\r';
	public static final char NEW_LINE = '\n';

	private final Map<String, List<String>> extensionHeaderMap = new HashMap<>();
	private final List<Via> viaList = new ArrayList<>();
	private AddressOfRecord from;
	private AddressOfRecord to;
	private AddressOfRecord referTo;
	private CommandSequence commandSequence;
	private Integer maxForwards;
	private int contentLength = 0;
	private ContactList contactList;
	private SipMediaType contentType;

	public void addExtensionHeader(String headerName, String value) {
		var lowerCasedHeader = headerName.trim().toLowerCase();
		extensionHeaderMap.compute(lowerCasedHeader, (s, strings) -> {
			if (strings == null) {
				strings = new ArrayList<>();
			}
			strings.add(value.trim());
			return strings;
		});
	}

	public Optional<List<String>> getExtensionHeaderValue(String headerName) {
		return Optional.ofNullable(extensionHeaderMap.get(headerName));
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
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		var that = (SipResponseHeaders) o;
		return contentLength == that.contentLength && Objects.equals(extensionHeaderMap, that.extensionHeaderMap) && Objects.equals(from, that.from)
				&& Objects.equals(to, that.to) && Objects.equals(referTo, that.referTo) && Objects.equals(commandSequence, that.commandSequence)
				&& Objects.equals(maxForwards, that.maxForwards) && Objects.equals(viaList, that.viaList) && Objects.equals(contactList,
				that.contactList) && Objects.equals(contentType, that.contentType);
	}

	@Override
	public int hashCode() {
		return Objects.hash(extensionHeaderMap, from, to, referTo, commandSequence, maxForwards, contentLength, viaList, contactList, contentType);
	}

	@Override
	public String toString() {
		return "SipHeaders{" +
				"headerMap=" + extensionHeaderMap +
				", viaList=" + viaList +
				", from=" + from +
				", to=" + to +
				", referTo=" + referTo +
				", commandSequence=" + commandSequence +
				", maxForwards=" + maxForwards +
				", contentLength=" + contentLength +
				", contactList=" + contactList +
				", contentType=" + contentType +
				'}';
	}

	@Override
	public void serialize(ByteBuffer dest) {
		serializeIfNeeded(FROM, from, dest);
		serializeIfNeeded(TO, to, dest);
		serializeIfNeeded(REFER_TO, referTo, dest);
		serializeIfNeeded(COMMAND_SEQUENCE, commandSequence, dest);
		serializeIfNeeded(CONTACT, contactList, dest);
	}

	@Override
	public int getSize() {
		int total = CRLF_LENGTH;
		total += calculateHeaderFieldSize(FROM, from);
		total += calculateHeaderFieldSize(TO, to);
		total += calculateHeaderFieldSize(REFER_TO, commandSequence);
		total += calculateHeaderFieldSize(COMMAND_SEQUENCE, commandSequence);
		total += calculateHeaderFieldSize(CONTACT, contactList);
		return total;
	}

	private int calculateHeaderFieldSize(String headerName, Serializable value) {
		if (value == null) {
			return 0;
		}
		return headerName.length() + COLON_LENGTH + value.getSize() + CRLF_LENGTH;
	}

	private void serializeIfNeeded(String headerName, Serializable serializable, ByteBuffer byteBuffer) {
		if (serializable == null) {
			return;
		}
		byteBuffer.put(headerName.getBytes(StandardCharsets.UTF_8));
		byteBuffer.put((byte) COLON);
		serializable.serialize(byteBuffer);
		byteBuffer.put((byte) CARRET);
		byteBuffer.put((byte) NEW_LINE);
	}



}
