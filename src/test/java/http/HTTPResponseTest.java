package http;

import http.domain.HTTPHeaders;
import http.domain.HTTPResponse;
import http.domain.HTTPResponseLine;
import http.domain.HTTPVersion;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HTTPResponseTest {

	@Test
	void serializeNoBody() {
		assertThat(
						new HTTPResponse(
										new HTTPResponseLine(new HTTPVersion(1, 1), 200, "OK"),
										new HTTPHeaders(),
										new byte[]{}
						).serialize()
		).isEqualTo(
			"HTTP/1.1 200 OK\r\n\r\n\r\n".getBytes(StandardCharsets.UTF_8)
		);
	}

	@Test
	void serializeBodyPresent() {
		byte[] body = {4, 1, 42, 127, 11, 35, 92, 11, 24, 98, 98, 55, 12};
		assertThat(
						new HTTPResponse(
										new HTTPResponseLine(new HTTPVersion(1, 1), 200, "OK"),
										new HTTPHeaders()
														.addSingleHeader("Content-Length", String.valueOf(body.length))
														.addSingleHeader("Content-Type", "application/json"),
										body
						).serialize()
		).isEqualTo(
						merge(
										"HTTP/1.1 200 OK\r\nContent-Length:%d\r\nContent-Type:application/json\r\n\r\n"
														.formatted(body.length)
														.getBytes(StandardCharsets.UTF_8),
										body
						));
	}

	private byte[] merge(byte[]... arrays) {
		int total = 0;
		for (byte[] array : arrays) {
			total += array.length;
		}
		byte[] res = new byte[total];
		int start = 0;
		for (byte[] array : arrays) {
			System.arraycopy(array, 0, res, start, array.length);
			start += array.length;
		}
		return res;
	}

}