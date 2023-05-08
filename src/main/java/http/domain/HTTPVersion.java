package http.domain;

import tcp.server.reader.exception.ParseException;
import util.Serializable;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public record HTTPVersion(int majorVersion, int minorVersion) implements Serializable {
	private static final Pattern HTTP_VERSION_PATTERN = Pattern.compile("HTTP/(\\d+)\\.(\\d+)", Pattern.CASE_INSENSITIVE);

	public static HTTPVersion parse(String httpVersion) throws ParseException {
		var matcher = HTTP_VERSION_PATTERN.matcher(httpVersion);
		if (!matcher.matches()) {
			throw new ParseException("""
							HTTP version doesn't match the format:
							HTTP-version  = HTTP-name "/" DIGIT "." DIGIT
							HTTP-name     = %x48.54.54.50 ; "HTTP", case-sensitive""");
		}
		return new HTTPVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
	}

	int getSizeInBytes() {
		return 8;
	}

	@Override
	public String toString() {
		return "HTTP/" + majorVersion + "." + minorVersion;
	}

	@Override
	public byte[] serialize() {
		return this.toString().getBytes(StandardCharsets.UTF_8);
	}
}
