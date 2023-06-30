package sip;

import javax.annotation.Nonnull;

import java.util.*;
import java.util.function.Consumer;


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


public class SipRequestHeaders {
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

	private final Map<String, Consumer<String>> headerSetterByHeaderName = Map.of(
			"from", v -> this.from = AddressOfRecord.parse(v),
			"to", v -> this.to = AddressOfRecord.parse(v),
			"refer-to", v -> this.referTo = AddressOfRecord.parse(v),
			"cseq", v -> this.commandSequence = CommandSequence.parse(v),
			"via", v -> viaList.addAll(Via.parseMultiple(v)),
			"content-length", v -> contentLength = Integer.parseInt(v.trim()),
			"max-forwards", v -> maxForwards = Integer.parseInt(v.trim()),
			"contact", v -> contactList = ContactList.parse(v),
			"content-type", v -> contentType = SipMediaType.parse(v),
			"call-id", v -> callId = v.trim()
	);

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

	public String getCallId() {
		return callId;
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
				'}';
	}

}
