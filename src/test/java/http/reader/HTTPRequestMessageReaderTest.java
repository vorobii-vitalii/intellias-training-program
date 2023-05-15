package http.reader;

import http.domain.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tcp.server.reader.exception.ParseException;
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
					new HTTPRequestMessageReader((name, val) -> Collections.singletonList(val.toString().trim()));

	@Test
	void readHappyPath() throws ParseException {
		byte[] bytes = "GET /example HTTP/1.1\r\nContent-Type:application/json\r\nHost: example.com\r\n\r\n"
										.getBytes(StandardCharsets.UTF_8);
		var readResult = httpRequestMessageReader.read(BufferTestUtils.createBufferContext(bytes), e -> {});
		assertThat(readResult).isNotNull();
		assertThat(readResult.first()).isEqualTo(
						new HTTPRequest(
										new HTTPRequestLine(HTTPMethod.GET, "/example", new HTTPVersion(1, 1)),
										new HTTPHeaders()
														.addSingleHeader("Content-Type", "application/json")
														.addSingleHeader("Host", "example.com"),
										new byte[] {}
						));
	}

	@Test
	void readHappyPathGivenBodyPresent() throws ParseException {
		byte[] bytes = ("GET /example HTTP/1.1\r\n" +
						"Content-Length:7\r\n" +
						"Content-Type:application/json\r\n" +
						"Host: example.com\r\n\r\n1234567"
		).getBytes(StandardCharsets.UTF_8);
		var readResult = httpRequestMessageReader.read(BufferTestUtils.createBufferContext(bytes), e -> {});
		assertThat(readResult).isNotNull();
		assertThat(readResult.first()).isEqualTo(
						new HTTPRequest(
										new HTTPRequestLine(HTTPMethod.GET, "/example", new HTTPVersion(1, 1)),
										new HTTPHeaders()
														.addSingleHeader("Content-Type", "application/json")
														.addSingleHeader("Content-Length", "7")
														.addSingleHeader("Host", "example.com"),
										"1234567".getBytes(StandardCharsets.UTF_8)
						));
	}

	@Test
	void readWrongHeaderMessage() {
		byte[] bytes = "GET /example HTTP/1.1\r\nContent-Type xxx  application/json\r\nHost: example.com\r\n\r\n"
						.getBytes(StandardCharsets.UTF_8);
		assertThrows(ParseException.class, () -> httpRequestMessageReader.read(BufferTestUtils.createBufferContext(bytes), e -> {}));
	}

	@Test
	void readNotEnoughBytes() throws ParseException {
		byte[] bytes = "GET /example HTTP/1.1\r\nContent-Type: application/json\r\nHost: example.com\r\n"
						.getBytes(StandardCharsets.UTF_8);
		assertThat(httpRequestMessageReader.read(BufferTestUtils.createBufferContext(bytes), e -> {})).isNull();
	}

}
