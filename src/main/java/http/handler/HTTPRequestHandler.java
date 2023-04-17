package http.handler;

import http.domain.*;
import http.post_processor.HTTPResponsePostProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import request_handler.NetworkRequest;
import request_handler.RequestHandler;

import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public class HTTPRequestHandler implements RequestHandler<HTTPRequest> {
	private static final Logger LOGGER = LogManager.getLogger(HTTPRequestHandler.class);

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
		LOGGER.info("Handling request {}", networkRequest);
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
