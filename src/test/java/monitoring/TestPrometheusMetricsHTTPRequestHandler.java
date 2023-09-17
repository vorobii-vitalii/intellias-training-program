package monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import http.domain.HTTPHeaders;
import http.domain.HTTPMethod;
import http.domain.HTTPRequest;
import http.domain.HTTPRequestLine;
import http.domain.HTTPVersion;
import http.monitoring.PrometheusMetricsHTTPRequestHandler;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import util.Constants;

@ExtendWith(MockitoExtension.class)
class TestPrometheusMetricsHTTPRequestHandler {
	private static final String METRICS_ENDPOINT = "/metrics";
	private static final String NOT_METRICS_ENDPOINT = "/hello";
	private static final HTTPRequest GET_METRICS_REQUEST = new HTTPRequest(
			new HTTPRequestLine(HTTPMethod.GET, METRICS_ENDPOINT, new HTTPVersion(1, 1)),
			new HTTPHeaders(),
			new byte[] {});
	private static final HTTPRequest NOT_GET_METRICS_REQUEST = new HTTPRequest(
			new HTTPRequestLine(HTTPMethod.GET, NOT_METRICS_ENDPOINT, new HTTPVersion(1, 1)),
			new HTTPHeaders(),
			new byte[] {});
	private static final String SCRAPED_METRICS = "SCRAPED_METRICS";

	@Mock
	PrometheusMeterRegistry prometheusMeterRegistry;

	PrometheusMetricsHTTPRequestHandler handler;

	@BeforeEach
	void init() {
		handler = new PrometheusMetricsHTTPRequestHandler(prometheusMeterRegistry, METRICS_ENDPOINT);
	}

	@Test
	void handleRequest() {
		when(prometheusMeterRegistry.scrape()).thenReturn(SCRAPED_METRICS);
		var httpResponse = handler.handleRequest(GET_METRICS_REQUEST);
		assertThat(httpResponse.httpHeaders().getHeaderValue(Constants.HTTPHeaders.CONTENT_LENGTH)).contains(String.valueOf(SCRAPED_METRICS.length()));
		assertThat(httpResponse.body()).isEqualTo(SCRAPED_METRICS.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void supportsGivenMetricsEndpoint() {
		assertThat(handler.supports(GET_METRICS_REQUEST)).isTrue();
	}

	@Test
	void supportsGivenNotMetricsEndpoint() {
		assertThat(handler.supports(NOT_GET_METRICS_REQUEST)).isFalse();
	}
}
