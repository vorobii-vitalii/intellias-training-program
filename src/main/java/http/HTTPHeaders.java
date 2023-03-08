package http;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HTTPHeaders {

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

	public void addHeader(String key, String value) {
		headers.put(key, Collections.singletonList(value));
	}

	public List<String> getHeaderValues(String header) {
		return headers.getOrDefault(header, Collections.emptyList());
	}

}
