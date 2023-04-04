package http;

import util.Serializable;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class HTTPHeaders implements Serializable {

	/**
	 * header-field   = field-name ":" OWS field-value OWS
	 *      field-name     = token
	 *      field-value    = *( field-content / obs-fold )
	 *      field-content  = field-vchar [ 1*( SP / HTAB ) field-vchar ]
	 *      field-vchar    = VCHAR / obs-text
	 *      obs-fold       = CRLF 1*( SP / HTAB )
	 *                     ; obsolete line folding
	 *                     ; see Section 3.2.4
	 *
	 *      OWS            = *( SP / HTAB )
	 *                     ; optional whitespace
	 *      RWS            = 1*( SP / HTAB )
	 *                     ; required whitespace
	 *      BWS            = OWS
	 *                     ; "bad" whitespace
	 *
	 *                     No whitespace is allowed between the header field-name and colon.  In
	 *    the past, differences in the handling of such whitespace have led to
	 *    security vulnerabilities in request routing and response handling.  A
	 *    server MUST reject any received request message that contains
	 *    whitespace between a header field-name and colon with a response code
	 *    of 400 (Bad Request).
	 */
	private final Map<String, List<String>> headers = new HashMap<>();

	public HTTPHeaders addSingleHeader(String key, String value) {
		headers.put(key, Collections.singletonList(value.trim()));
		return this;
	}

	public HTTPHeaders addHeaders(String key, List<String> values) {
		headers.put(key, values);
		return this;
	}

	public List<String> getHeaderValues(String header) {
		return headers.getOrDefault(header, Collections.emptyList());
	}

	public Optional<String> getHeaderValue(String header) {
		List<String> headerValues = getHeaderValues(header);
		if (headerValues.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(headerValues.get(0));
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
		return "HTTPHeaders{" +
						"headers=" + headers +
						'}';
	}
}
