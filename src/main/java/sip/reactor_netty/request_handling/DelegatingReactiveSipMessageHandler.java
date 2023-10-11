package sip.reactor_netty.request_handling;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import request_handler.ReactiveMessageHandler;
import reactor.core.publisher.Flux;
import sip.SipMessage;
import sip.SipRequest;
import sip.SipResponse;
import sip.reactor_netty.WSOutbound;

public class DelegatingReactiveSipMessageHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(DelegatingReactiveSipMessageHandler.class);

	private final Map<String, Collection<ReactiveMessageHandler<String, SipRequest, SipMessage, WSOutbound>>> requestHandlersMap;
	private final Map<String, Collection<ReactiveMessageHandler<String, SipResponse, SipMessage, WSOutbound>>> responseHandlersMap;

	public DelegatingReactiveSipMessageHandler(
			Map<String, Collection<ReactiveMessageHandler<String, SipRequest, SipMessage, WSOutbound>>> requestHandlersMap,
			Map<String, Collection<ReactiveMessageHandler<String, SipResponse, SipMessage, WSOutbound>>> responseHandlersMap
	) {
		this.requestHandlersMap = requestHandlersMap;
		this.responseHandlersMap = responseHandlersMap;
	}

	public Flux<? extends SipMessage> handleMessage(SipMessage incomingMessage, WSOutbound wsOutbound) {
		if (incomingMessage instanceof SipRequest sipRequest) {
			return handle(
					sipRequest,
					wsOutbound,
					s -> s.requestLine().method(),
					requestHandlersMap
			);
		}
		else if (incomingMessage instanceof SipResponse sipResponse) {
//			LOGGER.info("Handling response method = {} handlers = {}", sipResponse.headers().getCommandSequence().commandName(), responseHandlersMap);
			return handle(
					sipResponse,
					wsOutbound,
					s -> s.headers().getCommandSequence().commandName(),
					responseHandlersMap
			);
		}
		else {
			LOGGER.warn("Unrecognized message {}", incomingMessage);
			return Flux.error(new IllegalStateException("Unrecognized message"));
		}
	}

	private <T extends SipMessage> Flux<? extends SipMessage> handle(
			T message,
			WSOutbound wsOutbound,
			Function<T, String> methodExtractor,
			Map<String, Collection<ReactiveMessageHandler<String, T, SipMessage, WSOutbound>>> handlers
	) {
		var method = methodExtractor.apply(message);
		if (!handlers.containsKey(method)) {
			LOGGER.warn("No handler for method {}", method);
			return Flux.empty();
		}
		return handlers.get(method)
				.stream()
				.filter(s -> s.canHandle(message))
				.findFirst()
				.map(v -> v.handleMessage(message, wsOutbound))
				.orElseGet(() -> {
					LOGGER.warn("No matching handler for method = {}", method);
					return Flux.empty();
				});
	}

}
