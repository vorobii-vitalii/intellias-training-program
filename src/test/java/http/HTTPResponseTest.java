package http;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import http.domain.HTTPHeaders;
import http.domain.HTTPResponse;
import http.domain.HTTPResponseLine;
import http.domain.HTTPVersion;

class HTTPResponseTest {

	@Test
	void serializeNoBody() {
		var httpResponse = new HTTPResponse(
				new HTTPResponseLine(new HTTPVersion(1, 1), 200, "OK"),
				new HTTPHeaders(),
				new byte[] {}
		);
		var buffer = ByteBuffer.allocate(httpResponse.getSize());
		httpResponse.serialize(buffer);

		assertThat(buffer.array()).isEqualTo("HTTP/1.1 200 OK\r\n\r\n".getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void serializeBodyPresent() {
		byte[] body = {4, 1, 42, 127, 11, 35, 92, 11, 24, 98, 98, 55, 12};
		var httpResponse = new HTTPResponse(
				new HTTPResponseLine(new HTTPVersion(1, 1), 200, "OK"),
				new HTTPHeaders()
						.addSingleHeader("Content-Length", String.valueOf(body.length))
						.addSingleHeader("Content-Type", "application/json"),
				body
		);
		var buffer = ByteBuffer.allocate(httpResponse.getSize());
		httpResponse.serialize(buffer);
		assertThat(buffer.array())
				.isEqualTo(
						merge(
								"HTTP/1.1 200 OK\r\ncontent-length:%d\r\ncontent-type:application/json\r\n\r\n"
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