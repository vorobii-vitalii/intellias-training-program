package http.handler;

import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import http.domain.HTTPHeaders;
import http.domain.HTTPRequest;
import http.domain.HTTPResponse;
import http.domain.HTTPResponseLine;
import http.domain.HTTPVersion;
import http.post_processor.HTTPResponsePostProcessor;
import request_handler.NetworkRequest;
import request_handler.RequestHandler;

public class HTTPRequestHandler implements RequestHandler<HTTPRequest> {
	private final List<HTTPRequestHandlerStrategy> httpRequestHandlerStrategies;
	private final Collection<HTTPResponsePostProcessor> httpResponsePostProcessor;

	public HTTPRequestHandler(
					List<HTTPRequestHandlerStrategy> httpRequestHandlerStrategies,
					Collection<HTTPResponsePostProcessor> httpResponsePostProcessor
	) {
		this.httpResponsePostProcessor = httpResponsePostProcessor;
		this.httpRequestHandlerStrategies = new ArrayList<>(httpRequestHandlerStrategies);
		this.httpRequestHandlerStrategies.sort(Comparator.comparing(HTTPRequestHandlerStrategy::getPriority));
	}

	@Override
	public void handle(NetworkRequest<HTTPRequest> networkRequest) {
		var response = httpRequestHandlerStrategies
				.stream()
				.filter(strategy -> strategy.supports(networkRequest.request()))
				.findFirst()
				.map(strategy -> strategy.handleRequest(networkRequest.request()))
				.orElseGet(getNotFoundRequestHandler(networkRequest));
		httpResponsePostProcessor.forEach(handlerStrategy -> handlerStrategy.handle(networkRequest, response));
		var socketConnection = networkRequest.socketConnection();
		socketConnection.appendResponse(response);
		socketConnection.changeOperation(SelectionKey.OP_WRITE);
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
