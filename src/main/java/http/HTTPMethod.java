package http;

import exception.ParseException;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum HTTPMethod {
	GET,
	POST,
	PUT,
	PATCH,
	DELETE;

	private static final Map<String, HTTPMethod> METHOD_BY_STRING = Stream.of(HTTPMethod.values())
					.collect(Collectors.toMap(httpMethod -> httpMethod.name().toLowerCase(), Function.identity()));

	public static HTTPMethod parse(String method) throws ParseException {
		if (!METHOD_BY_STRING.containsKey(method.toLowerCase())) {
			throw new ParseException("Method " + method + " is not supported!");
		}
		return METHOD_BY_STRING.get(method.toLowerCase());
	}

}
