package http.domain;

import util.Serializable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class HTTPHeaders implements Serializable {
	private static final byte CARRIAGE_RETURN = '\r';
	private static final byte LINE_FEED = '\n';
	private static final int CLRF_BYTES = 2;
	public static final int COLON = 1;
	public static final byte COLON_BYTE = (byte) ':';

	/**
	 * header-field   = field-name ":" OWS field-value OWS
	 *      field-name     = token
	 *      field-value    = *( field-content / obs-fold )
	 *      field-content  = field-vchar [ 1*( SP / HTAB ) field-vchar ]
	 *      field-vchar    = VCHAR / obs-text
	 *      obs-fold       = CRLF 1*( SP / HTAB )
	 *                     ; obsolete line folding
	 *                     ; see Section 3.2.4
	 *      OWS            = *( SP / HTAB )
	 *                     ; optional whitespace
	 *      RWS            = 1*( SP / HTAB )
	 *                     ; required whitespace
	 *      BWS            = OWS
	 *                     ; "bad" whitespace
	 *                     No whitespace is allowed between the header field-name and colon.  In
	 *    the past, differences in the handling of such whitespace have led to
	 *    security vulnerabilities in request routing and response handling.  A
	 *    server MUST reject any received request message that contains
	 *    whitespace between a header field-name and colon with a response code
	 *    of 400 (Bad Request).
	 */
	private final Map<String, String> headers = new HashMap<>();

	public HTTPHeaders addSingleHeader(String key, String value) {
		headers.put(key, value.trim());
		return this;
	}

	public Optional<String> getHeaderValue(String header) {
		return Optional.ofNullable(headers.get(header));
	}

	@Override
	public int getSize() {
		var count = CLRF_BYTES;
		for (var entry : headers.entrySet()) {
			count += entry.getKey().length();
			count += COLON;
			count += entry.getValue().length();
			count += CLRF_BYTES;
		}
		return count;
	}

	@Override
	public void serialize(ByteBuffer dest) {
		for (var entry : headers.entrySet()) {
			dest.put(entry.getKey().getBytes(StandardCharsets.UTF_8));
			dest.put(COLON_BYTE);
			dest.put(entry.getValue().getBytes(StandardCharsets.UTF_8));
			dest.put(CARRIAGE_RETURN);
			dest.put(LINE_FEED);
		}
		dest.put(CARRIAGE_RETURN);
		dest.put(LINE_FEED);
	}

	@Override
	public byte[] serialize() {
		return (this.headers.entrySet().stream()
						.map(e -> {
							String value = String.join(" ", e.getValue());
							String key = e.getKey();
							return key + ":" + value;
						})
						.collect(Collectors.joining("\r\n", "", "\r\n\r\n"))).getBytes(StandardCharsets.US_ASCII);
	}

	@Override
	public String toString() {
		return "HTTPHeaders{" + "headers=" + headers + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		var that = (HTTPHeaders) o;
		return Objects.equals(headers, that.headers);
	}

	@Override
	public int hashCode() {
		return Objects.hash(headers);
	}
}
