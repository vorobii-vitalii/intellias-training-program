package sip;

import javax.annotation.Nonnull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.common.collect.ImmutableMap;

import util.Serializable;

//8.1.1.9 Supported and Require
//
//		If the UAC supports extensions to SIP that can be applied by the
//		server to the response, the UAC SHOULD include a Supported header
//		field in the request listing the option tags (Section 19.2) for those
//		extensions.
//
//		The option tags listed MUST only refer to extensions defined in
//		standards-track RFCs.  This is to prevent servers from insisting that
//		clients implement non-standard, vendor-defined features in order to
//		receive service.  Extensions defined by experimental and
//		informational RFCs are explicitly excluded from usage with the
//		Supported header field in a request, since they too are often used to
//		document vendor-defined extensions.
//
//		If the UAC wishes to insist that a UAS understand an extension that
//		the UAC will apply to the request in order to process the request, it
//		MUST insert a Require header field into the request listing the
//		option tag for that extension.  If the UAC wishes to apply an
//		extension to the request and insist that any proxies that are
//
//
//
//		Rosenberg, et. al.          Standards Track                    [Page 40]
//
//
//		RFC 3261            SIP: Session Initiation Protocol           June 2002
//
//
//		traversed understand that extension, it MUST insert a Proxy-Require
//		header field into the request listing the option tag for that
//		extension.
//
//		As with the Supported header field, the option tags in the Require
//		and Proxy-Require header fields MUST only refer to extensions defined
//		in standards-track RFCs.


public class SipRequestHeaders implements Serializable, Cloneable<SipRequestHeaders> {
	public static final char COLON = ':';
	public static final char CARRET = '\r';
	public static final char NEW_LINE = '\n';
	public static final int CRLF_LENGTH = 2;
	public static final int COLON_LENGTH = 1;
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
	public static final String EXPIRES = "expires";
	public static final String RECORD_ROUTE = "record-route";

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
	private String callId;
	private Integer expires;
	private final List<AddressOfRecord> recordRoutes = new ArrayList<>();

	public SipRequestHeaders overrideWith(SipRequestHeaders overrideHeaders) {
		var sipRequestHeaders = new SipRequestHeaders();
		sipRequestHeaders.setFrom(Optional.ofNullable(overrideHeaders.getFrom()).orElse(from));
		sipRequestHeaders.setTo(Optional.ofNullable(overrideHeaders.getTo()).orElse(to));
		sipRequestHeaders.setReferTo(Optional.ofNullable(overrideHeaders.getReferTo()).orElse(referTo));
		sipRequestHeaders.setCommandSequence(Optional.ofNullable(overrideHeaders.getCommandSequence()).orElse(commandSequence));
		for (var via : viaList) {
			sipRequestHeaders.addVia(via.normalize());
		}
		sipRequestHeaders.setContactList(Optional.ofNullable(overrideHeaders.getContactList()).orElse(contactList));
		sipRequestHeaders.setContentType(Optional.ofNullable(overrideHeaders.getContentType()).orElse(contentType));
		sipRequestHeaders.setCallId(Optional.ofNullable(overrideHeaders.getCallId()).orElse(callId));
		sipRequestHeaders.setMaxForwards(Optional.ofNullable(overrideHeaders.getMaxForwards()).orElse(maxForwards));
		sipRequestHeaders.setContentLength(overrideHeaders.getContentLength() > 0 ? overrideHeaders.getContentLength() : contentLength);
		for (var entry : extensionHeaderMap.entrySet()) {
			for (var value : entry.getValue()) {
				sipRequestHeaders.addSingleHeader(entry.getKey(), value);
			}
		}
		for (var entry : overrideHeaders.extensionHeaderMap.entrySet()) {
			for (var value : entry.getValue()) {
				sipRequestHeaders.addSingleHeader(entry.getKey(), value);
			}
		}
		return sipRequestHeaders;
	}

	@Override
	public SipRequestHeaders replicate() {
		var sipRequestHeaders = new SipRequestHeaders();
		sipRequestHeaders.setFrom(from);
		sipRequestHeaders.setTo(to);
		sipRequestHeaders.setReferTo(referTo);
		sipRequestHeaders.setCommandSequence(commandSequence);
		for (var via : viaList) {
			sipRequestHeaders.addVia(via.normalize());
		}
		sipRequestHeaders.setContactList(contactList);
		sipRequestHeaders.setContentType(contentType);
		sipRequestHeaders.setCallId(callId);
		sipRequestHeaders.setContentLength(contentLength);
		sipRequestHeaders.setMaxForwards(maxForwards);
		for (var entry : extensionHeaderMap.entrySet()) {
			for (var value : entry.getValue()) {
				sipRequestHeaders.addSingleHeader(entry.getKey(), value);
			}
		}
		return sipRequestHeaders;
	}

	public SipResponseHeaders toResponseHeaders() {
		var sipResponseHeaders = new SipResponseHeaders();
		sipResponseHeaders.setFrom(from);
		sipResponseHeaders.setTo(to);
		sipResponseHeaders.setReferTo(referTo);
		sipResponseHeaders.setCommandSequence(commandSequence);
		for (var via : viaList) {
			sipResponseHeaders.addVia(via.normalize());
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
			.put("expires", v -> expires = Integer.parseInt(v.trim()))
			.build();

	public void addSingleHeader(String headerName, String value) {
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

	public Boolean getBooleanExtensionHeader(String headerName) {
		if (extensionHeaderMap.containsKey(headerName)) {
			return Boolean.parseBoolean(extensionHeaderMap.get(headerName).get(0));
		}
		return null;
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

	public void addRecordRoute(AddressOfRecord addressOfRecord) {
		recordRoutes.add(addressOfRecord);
	}

	public void addRecordRouteFront(AddressOfRecord addressOfRecord) {
		recordRoutes.add(0, addressOfRecord);
	}

	public void addVia(Via via) {
		viaList.add(via);
	}

	public void addViaFront(Via via) {
		viaList.add(0, via);
	}

	public List<AddressOfRecord> getRecordRoutes() {
		return recordRoutes;
	}

	public String getCallId() {
		return callId;
	}

	public Integer getExpires() {
		return expires;
	}

	public SipRequestHeaders setExpires(Integer expires) {
		this.expires = expires;
		return this;
	}

	public void setCallId(String callId) {
		this.callId = callId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		SipRequestHeaders that = (SipRequestHeaders) o;
		return contentLength == that.contentLength && Objects.equals(extensionHeaderMap, that.extensionHeaderMap) && Objects.equals(from, that.from)
				&& Objects.equals(to, that.to) && Objects.equals(referTo, that.referTo) && Objects.equals(commandSequence, that.commandSequence)
				&& Objects.equals(maxForwards, that.maxForwards) && Objects.equals(viaList, that.viaList) && Objects.equals(contactList,
				that.contactList) && Objects.equals(contentType, that.contentType) && Objects.equals(callId, that.callId) && Objects.equals(expires, that.expires);
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
				", expires=" + expires +
				", recordRoutes=" + recordRoutes +
				'}';
	}

	@Override
	public void serialize(ByteBuffer dest) {
		serializeIfNeeded(FROM, from, dest);
		serializeIfNeeded(TO, to, dest);
		serializeIfNeeded(REFER_TO, referTo, dest);
		serializeIfNeeded(COMMAND_SEQUENCE, commandSequence, dest);
		serializeIfNeeded(CONTACT, contactList, dest);
		serializeIfNeeded(CONTENT_TYPE, contentType, dest);
		serializeIfNeeded(MAX_FORWARDS, maxForwards, dest, v -> String.valueOf(v).getBytes(StandardCharsets.UTF_8));
		serializeIfNeeded(EXPIRES, expires, dest, v -> String.valueOf(v).getBytes(StandardCharsets.UTF_8));
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
		total += calculateHeaderFieldSize(EXPIRES, expires, v -> String.valueOf(v).length());
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
}
