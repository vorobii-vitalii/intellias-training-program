package sip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SipHeaders {
	private final Map<String, List<String>> headerMap = new HashMap<>();
	public void addSingleHeader(String headerName, String value) {
		headerMap.compute(headerName, (s, strings) -> {
			if (strings == null) {
				strings = new ArrayList<>();
			}
			strings.add(value);
			return strings;
		});
	}

	public Optional<String> getHeaderValue(String headerName) {
		return Optional.ofNullable(headerMap.get(headerName))
				.map(v -> {
					if (v.size() > 1) {
						throw new IllegalStateException("More than 1 value for header = " + headerName);
					}
					return v.get(0);
				});
	}
}
