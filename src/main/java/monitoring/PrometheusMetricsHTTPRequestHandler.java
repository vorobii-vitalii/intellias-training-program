package monitoring;

import java.nio.charset.StandardCharsets;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import http.domain.HTTPHeaders;
import http.domain.HTTPRequest;
import http.domain.HTTPResponse;
import http.domain.HTTPResponseLine;
import http.domain.HTTPVersion;
import http.handler.HTTPRequestHandlerStrategy;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import util.Constants;

public class PrometheusMetricsHTTPRequestHandler implements HTTPRequestHandlerStrategy {
	private static final Logger LOGGER = LoggerFactory.getLogger(PrometheusMetricsHTTPRequestHandler.class);

	private final PrometheusMeterRegistry prometheusRegistry;
	private final String prometheusEndpoint;

	@Override
	public boolean supports(HTTPRequest httpRequest) {
		return httpRequest.getHttpRequestLine().path().equals(prometheusEndpoint);
	}

	public PrometheusMetricsHTTPRequestHandler(PrometheusMeterRegistry prometheusRegistry, String prometheusEndpoint) {
		this.prometheusRegistry = prometheusRegistry;
		this.prometheusEndpoint = prometheusEndpoint;
	}

	@Override
	public HTTPResponse handleRequest(HTTPRequest request) {
		var scrapeBytes = prometheusRegistry.scrape().getBytes(StandardCharsets.UTF_8);
		LOGGER.info("Scraped bytes = {}", scrapeBytes.length);
		return new HTTPResponse(
				new HTTPResponseLine(new HTTPVersion(1, 1), Constants.HTTPStatusCode.OK, "OK"),
				new HTTPHeaders().addSingleHeader(Constants.HTTPHeaders.CONTENT_LENGTH, String.valueOf(scrapeBytes.length)),
				scrapeBytes
		);
	}

	@Override
	public int getPriority() {
		return 0;
	}
}
