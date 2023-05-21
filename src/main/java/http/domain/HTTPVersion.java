package http.domain;

import tcp.server.reader.exception.ParseException;
import util.Serializable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public record HTTPVersion(int majorVersion, int minorVersion) implements Serializable {
	private static final Pattern HTTP_VERSION_PATTERN = Pattern.compile("HTTP/(\\d+)\\.(\\d+)", Pattern.CASE_INSENSITIVE);
	public static final String HTTP_PREFIX = "HTTP/";
	public static final byte[] HTTP_PREFIX_BYTES = HTTP_PREFIX.getBytes(StandardCharsets.UTF_8);
	private static final byte DOT = '.';

	public static HTTPVersion parse(CharSequence httpVersion) throws ParseException {
		if (httpVersion.length() != 8) {
			throw new ParseException("""
							HTTP version doesn't match the format:
							HTTP-version  = HTTP-name "/" DIGIT "." DIGIT
							HTTP-name     = %x48.54.54.50 ; "HTTP", case-sensitive""");
		}
		return new HTTPVersion(httpVersion.charAt(5) - '0', httpVersion.charAt(7) - '0');
//		var matcher = HTTP_VERSION_PATTERN.matcher(httpVersion);
//		if (!matcher.matches()) {
//			throw new ParseException("""
//							HTTP version doesn't match the format:
//							HTTP-version  = HTTP-name "/" DIGIT "." DIGIT
//							HTTP-name     = %x48.54.54.50 ; "HTTP", case-sensitive""");
//		}
//		return new HTTPVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
	}

	@Override
	public void serialize(ByteBuffer dest) {
		dest.put(HTTP_PREFIX_BYTES);
		dest.put((byte) (majorVersion + '0'));
		dest.put(DOT);
		dest.put((byte) (minorVersion + '0'));
	}

	@Override
	public int getSize() {
		return HTTP_PREFIX_BYTES.length + 1 + 1 + 1;
	}

	@Override
	public String toString() {
		return HTTP_PREFIX + majorVersion + "." + minorVersion;
	}

	@Override
	public byte[] serialize() {
		return this.toString().getBytes(StandardCharsets.UTF_8);
	}
}
