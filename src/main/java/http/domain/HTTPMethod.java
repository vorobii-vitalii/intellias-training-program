package http.domain;

import tcp.server.reader.exception.ParseException;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum HTTPMethod {
	GET,
	POST,
	PUT,
	PATCH,
	HEAD,
	CONNECT,
	DELETE;

	private static final Map<CharSequence, HTTPMethod> METHOD_BY_STRING = Stream.of(HTTPMethod.values())
					.collect(Collectors.toMap(Enum::name, Function.identity()));

	public static HTTPMethod parse(CharSequence method) throws ParseException {
		if (!METHOD_BY_STRING.containsKey(method)) {
			throw new ParseException("Method " + method + " is not supported!");
		}
		return METHOD_BY_STRING.get(method);
	}

}
