package http.handler;

import http.*;
import request_handler.RequestHandler;
import request_handler.ProcessingRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class HTTPRequestHandler implements RequestHandler<HTTPRequest, HTTPResponse> {
	private final List<HTTPRequestHandlerStrategy> httpRequestHandlerStrategies;

	public HTTPRequestHandler(List<HTTPRequestHandlerStrategy> httpRequestHandlerStrategies) {
		httpRequestHandlerStrategies.sort((a, b) -> b.getPriority() - a.getPriority());
		this.httpRequestHandlerStrategies = httpRequestHandlerStrategies;
	}

	@Override
	public void handle(ProcessingRequest<HTTPRequest, HTTPResponse> processingRequest) {
		var response = httpRequestHandlerStrategies
						.stream()
						.filter(strategy -> strategy.supports(processingRequest.getRequest()))
						.findFirst()
						.map(strategy -> strategy.handleRequest(processingRequest.getRequest()))
						.orElseGet(() -> {
							var bytes = """
													<html>
															<header>
																	<title>Not found</title>
															</header>
															<h2>Requested resource not found</h2>
															<p>Request: %s</p>
													</html>
											""".formatted(processingRequest.getRequest()).getBytes(StandardCharsets.UTF_8);
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
						});
		processingRequest.onResponse(response);
	}

}
