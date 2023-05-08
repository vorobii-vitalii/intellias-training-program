package http.handler;

import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import http.domain.HTTPHeaders;
import http.domain.HTTPRequest;
import http.domain.HTTPResponse;
import http.domain.HTTPResponseLine;
import http.domain.HTTPVersion;
import http.post_processor.HTTPResponsePostProcessor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import request_handler.NetworkRequest;
import request_handler.RequestHandler;

public class HTTPRequestHandler implements RequestHandler<HTTPRequest> {
	private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	private final List<HTTPRequestHandlerStrategy> httpRequestHandlerStrategies;
	private final Collection<HTTPResponsePostProcessor> httpResponsePostProcessor;
	private final Tracer httpRequestHandlerTracer;

	public HTTPRequestHandler(
			List<HTTPRequestHandlerStrategy> httpRequestHandlerStrategies,
			Collection<HTTPResponsePostProcessor> httpResponsePostProcessor,
			OpenTelemetry openTelemetry
	) {
		this.httpResponsePostProcessor = httpResponsePostProcessor;
		this.httpRequestHandlerStrategies = new ArrayList<>(httpRequestHandlerStrategies);
		this.httpRequestHandlerStrategies.sort(Comparator.comparing(HTTPRequestHandlerStrategy::getPriority));
		httpRequestHandlerTracer = openTelemetry.getTracer("HTTP Request Handler");
	}

	@Override
	public void handle(NetworkRequest<HTTPRequest> networkRequest) {
		executorService.execute(() -> {
			var httpRequest = networkRequest.request();
			var requestSpan = httpRequestHandlerTracer.spanBuilder(httpRequest.getHttpRequestLine().toString())
					.setAttribute(SemanticAttributes.HTTP_METHOD, httpRequest.getHttpRequestLine().httpMethod().toString())
					.setSpanKind(SpanKind.SERVER)
					.setParent(Context.current().with(networkRequest.span()))
					.startSpan();
			try {
				var response = httpRequestHandlerStrategies
						.stream()
						.filter(strategy -> strategy.supports(httpRequest))
						.findFirst()
						.map(strategy -> strategy.handleRequest(httpRequest))
						.orElseGet(getNotFoundRequestHandler(networkRequest));
				requestSpan.addEvent("Response created");
				httpResponsePostProcessor.forEach(handlerStrategy -> handlerStrategy.handle(networkRequest, response));
				requestSpan.addEvent("Postprocessors finished");
				var socketConnection = networkRequest.socketConnection();
				socketConnection.appendResponse(response, networkRequest.span());
				socketConnection.changeOperation(SelectionKey.OP_WRITE);
				requestSpan.addEvent("Response added to queue");
			}
			finally {
				requestSpan.end();
			}
		});
	}

	private Supplier<HTTPResponse> getNotFoundRequestHandler(NetworkRequest<HTTPRequest> networkRequest) {
		return () -> {
			var bytes = """
									<html>
											<header>
													<title>Not found</title>
											</header>
											<h2>Requested resource not found</h2>
											<p>Request: %s</p>
									</html>
							""".formatted(networkRequest.request()).getBytes(StandardCharsets.UTF_8);
			return new HTTPResponse(
							new HTTPResponseLine(
											new HTTPVersion(1, 1),
											404,
											"Not found"
							),
							new HTTPHeaders()
											.addSingleHeader("Content-Type", "text/html")
											.addSingleHeader("Content-Length", String.valueOf(bytes.length)),
							bytes
			);
		};
	}

}
