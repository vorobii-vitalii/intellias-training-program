package sip;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableMap;

import util.Serializable;

public class SipResponseHeaders implements Serializable, Cloneable<SipResponseHeaders> {
	private static final String FROM = "from";
	private static final String TO = "to";
	private static final String REFER_TO = "refer-to";
	private static final String COMMAND_SEQUENCE = "cseq";
	private static final String VIA = "Via";
	private static final String CONTENT_LENGTH = "content-length";
	private static final String MAX_FORWARDS = "max-forwards";
	private static final String CONTACT = "contact";
	private static final String CONTENT_TYPE = "content-type";
	private static final String CALL_ID = "call-id";
	public static final int CRLF_LENGTH = 2;
	public static final int COLON_LENGTH = 1;
	public static final char COLON = ':';
	public static final char CARRET = '\r';
	public static final char NEW_LINE = '\n';

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
	public static final String RECORD_ROUTE = "record-route";

	private final Map<String, List<String>> extensionHeaderMap = new HashMap<>();
	private final Deque<Via> viaList = new LinkedList<>();
	private AddressOfRecord from;
	private AddressOfRecord to;
	private AddressOfRecord referTo;
	private CommandSequence commandSequence;
	private Integer maxForwards;
	private int contentLength = 0;
	private ContactList contactList;
	private SipMediaType contentType;
	private String callId;
	private final List<AddressOfRecord> recordRoutes = new ArrayList<>();

	private final Map<String, Consumer<String>> headerSetterByHeaderName = ImmutableMap.<String, Consumer<String>>builder()
			.put("from", v -> this.from = AddressOfRecord.parse(v))
			.put("to", v -> this.to = AddressOfRecord.parse(v))
			.put("refer-to", v -> this.referTo = AddressOfRecord.parse(v))
			.put("cseq", v -> this.commandSequence = CommandSequence.parse(v))
			.put("via", v -> viaList.addAll(Via.parseMultiple(v)))
			.put("content-length", v -> contentLength = Integer.parseInt(v.trim()))
			.put("max-forwards", v -> maxForwards = Integer.parseInt(v.trim()))
			.put("contact", v -> contactList = ContactList.parse(v))
			.put("content-type", v -> contentType = SipMediaType.parse(v))
			.put("call-id", v -> callId = v.trim())
			.put("record-route", v -> recordRoutes.add(AddressOfRecord.parse(v.trim())))
			.build();

	@Override
	public void serialize(ByteBuffer dest) {
		serializeIfNeeded(FROM, from, dest);
		serializeIfNeeded(TO, to, dest);
		serializeIfNeeded(REFER_TO, referTo, dest);
		serializeIfNeeded(COMMAND_SEQUENCE, commandSequence, dest);
		serializeIfNeeded(CONTACT, contactList, dest);
		serializeIfNeeded(CONTENT_TYPE, contentType, dest);
		serializeIfNeeded(MAX_FORWARDS, maxForwards, dest, v -> String.valueOf(v).getBytes(StandardCharsets.UTF_8));
		serializeIfNeeded(CONTENT_LENGTH, contentLength, dest, v -> String.valueOf(v).getBytes(StandardCharsets.UTF_8));
		serializeIfNeeded(CALL_ID, callId, dest, v -> v.getBytes(StandardCharsets.UTF_8));
		for (var via : viaList) {
			serializeIfNeeded(VIA, via, dest);
		}
		for (var sipUri : recordRoutes) {
			serializeIfNeeded(RECORD_ROUTE, sipUri, dest);
		}
		for (var entry : extensionHeaderMap.entrySet()){
			for (var value : entry.getValue()) {
				serializeIfNeeded(entry.getKey(), value, dest, v -> v.getBytes(StandardCharsets.UTF_8));
			}
		}
		dest.put((byte) CARRET);
		dest.put((byte) NEW_LINE);
	}

	@Override
	public int getSize() {
		int total = CRLF_LENGTH;
		total += calculateHeaderFieldSize(FROM, from);
		total += calculateHeaderFieldSize(TO, to);
		total += calculateHeaderFieldSize(REFER_TO, commandSequence);
		total += calculateHeaderFieldSize(COMMAND_SEQUENCE, commandSequence);
		total += calculateHeaderFieldSize(CONTACT, contactList);
		total += calculateHeaderFieldSize(CONTENT_TYPE, contentType);
		total += calculateHeaderFieldSize(MAX_FORWARDS, maxForwards, v -> String.valueOf(v).length());
		total += calculateHeaderFieldSize(CONTENT_LENGTH, contentLength, v -> String.valueOf(v).length());
		total += calculateHeaderFieldSize(CALL_ID, callId, String::length);
		for (var via : viaList) {
			total += calculateHeaderFieldSize(VIA, via);
		}
		for (var sipUri : recordRoutes) {
			total += calculateHeaderFieldSize(RECORD_ROUTE, sipUri);
		}
		for (var entry : extensionHeaderMap.entrySet()){
			for (var value : entry.getValue()) {
				total += calculateHeaderFieldSize(entry.getKey(), value, String::length);
			}
		}
		return total;
	}

	public void addHeader(String headerName, String value) {
		var lowerCasedHeader = headerName.toLowerCase();
		var formattedName = Optional.ofNullable(COMPACT_HEADERS_MAP.get(lowerCasedHeader)).orElse(lowerCasedHeader);
		var headerSetter = headerSetterByHeaderName.get(formattedName);
		if (headerSetter != null) {
			headerSetter.accept(value);
		} else {
			extensionHeaderMap.compute(formattedName, (s, strings) -> {
				if (strings == null) {
					strings = new ArrayList<>();
				}
				strings.add(value.trim());
				return strings;
			});
		}
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

	private <T> int calculateHeaderFieldSize(String headerName, T obj, Function<T, Integer> sizeExtractor) {
		if (obj == null) {
			return 0;
		}
		return headerName.length() + COLON_LENGTH + sizeExtractor.apply(obj) + CRLF_LENGTH;
	}

	private <T> void serializeIfNeeded(String headerName, T obj, ByteBuffer byteBuffer, Function<T, byte[]> bytesExtractor) {
		if (obj == null) {
			return;
		}
		byteBuffer.put(headerName.getBytes(StandardCharsets.UTF_8));
		byteBuffer.put((byte) COLON);
		byteBuffer.put(bytesExtractor.apply(obj));
		byteBuffer.put((byte) CARRET);
		byteBuffer.put((byte) NEW_LINE);
	}

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
	public Deque<Via> getViaList() {
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
		viaList.addLast(via);
	}

	public void addViaAtBeggining(Via via) {
		viaList.addFirst(via);
	}

	public void setCallId(String callId) {
		this.callId = callId;
	}

	public String getCallId() {
		return callId;
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
				that.contactList) && Objects.equals(contentType, that.contentType) && Objects.equals(callId, that.callId);
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
				", callId=" + callId +
				", recordRoutes=" + recordRoutes +
				'}';
	}

	@Override
	public SipResponseHeaders replicate() {
		var sipResponseHeaders = new SipResponseHeaders();
		sipResponseHeaders.setFrom(from);
		sipResponseHeaders.setTo(to);
		sipResponseHeaders.setReferTo(referTo);
		sipResponseHeaders.setCommandSequence(commandSequence);
		for (var via : viaList) {
			sipResponseHeaders.addVia(via.normalize());
		}
		for (var recordRoute : recordRoutes) {
			sipResponseHeaders.addRecordRoute(recordRoute);
		}
		sipResponseHeaders.setContactList(contactList);
		sipResponseHeaders.setContentType(contentType);
		sipResponseHeaders.setCallId(callId);
		sipResponseHeaders.setContentLength(contentLength);
		sipResponseHeaders.setMaxForwards(maxForwards);
		for (var entry : extensionHeaderMap.entrySet()) {
			for (var value : entry.getValue()) {
				sipResponseHeaders.addExtensionHeader(entry.getKey(), value);
			}
		}
		return sipResponseHeaders;
	}

	public void addRecordRouteFront(AddressOfRecord addressOfRecord) {
		recordRoutes.add(0, addressOfRecord);
	}

	public void addRecordRoute(AddressOfRecord sipUri) {
		recordRoutes.add(sipUri);
	}

}
