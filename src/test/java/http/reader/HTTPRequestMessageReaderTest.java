package http.reader;

import tcp.server.reader.exception.ParseException;
import http.domain.HTTPMethod;
import http.domain.HTTPRequest;
import http.domain.HTTPRequestLine;
import http.domain.HTTPVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import utils.BufferTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class HTTPRequestMessageReaderTest {

	HTTPRequestMessageReader httpRequestMessageReader =
					new HTTPRequestMessageReader((name, val) -> Collections.singletonList(val.trim()));

	@Test
	void readHappyPath() throws ParseException {
		byte[] bytes = "GET /example HTTP/1.1\r\nContent-Type:application/json\r\nHost: example.com\r\n\r\n"
										.getBytes(StandardCharsets.UTF_8);
		var readResult = httpRequestMessageReader.read(BufferTestUtils.createBufferContext(bytes));
		assertThat(readResult).isNotNull();
		assertThat(readResult.first()).isEqualTo(
						new HTTPRequest(new HTTPRequestLine(
										HTTPMethod.GET,
										"/example",
										new HTTPVersion(1, 1))
						)
						.addHeader("Content-Type", "application/json")
						.addHeader("Host", "example.com"));
	}

	@Test
	void readWrongHeaderMessage() {
		byte[] bytes = "GET /example HTTP/1.1\r\nContent-Type xxx  application/json\r\nHost: example.com\r\n\r\n"
						.getBytes(StandardCharsets.UTF_8);
		assertThrows(ParseException.class, () -> httpRequestMessageReader.read(BufferTestUtils.createBufferContext(bytes)));
	}

	@Test
	void readNotEnoughBytes() throws ParseException {
		byte[] bytes = "GET /example HTTP/1.1\r\nContent-Type: application/json\r\nHost: example.com\r\n"
						.getBytes(StandardCharsets.UTF_8);
		assertThat(httpRequestMessageReader.read(BufferTestUtils.createBufferContext(bytes))).isNull();
	}

}
