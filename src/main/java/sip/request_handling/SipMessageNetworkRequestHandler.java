package sip.request_handling;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import request_handler.NetworkRequest;
import request_handler.RequestHandler;
import sip.SipMessage;
import sip.SipRequest;
import sip.SipResponse;
import sip.request_handling.normalize.Normalizer;
import sip.request_handling.normalize.SipRequestNormalizeContext;

public class SipMessageNetworkRequestHandler implements RequestHandler<NetworkRequest<SipMessage>> {
	private static final Logger LOGGER = LoggerFactory.getLogger(SipMessageNetworkRequestHandler.class);

	private final Map<String, List<SipMessageHandler<SipRequest>>> requestHandlerMap = new HashMap<>();
	private final Normalizer<SipRequest, SipRequestNormalizeContext> sipRequestNormalizer;
	private final Collection<SipMessageHandler<SipResponse>> sipResponseHandlers;

	public SipMessageNetworkRequestHandler(
			Collection<SipRequestHandler> sipMessageHandlers,
			Collection<SipMessageHandler<SipResponse>> sipResponseHandlers,
			Normalizer<SipRequest, SipRequestNormalizeContext> sipRequestNormalizer
	) {
		for (var sipMessageHandler : sipMessageHandlers) {
			for (var handledType : sipMessageHandler.getHandledTypes()) {
				requestHandlerMap.computeIfAbsent(handledType, s -> new ArrayList<>());
				requestHandlerMap.get(handledType).add(sipMessageHandler);
			}
		}
		this.sipResponseHandlers = sipResponseHandlers;
		this.sipRequestNormalizer = sipRequestNormalizer;
	}

	@Override
	public void handle(NetworkRequest<SipMessage> request) {
		var sipMessage = request.request();
		if (sipMessage instanceof SipRequest sipRequest) {
			var context = new SipRequestNormalizeContext(request.socketConnection());
			var normalizerRequest = sipRequestNormalizer.normalize(sipRequest, context);
			LOGGER.info("Received request {}", normalizerRequest);
			var requestMethod = normalizerRequest.requestLine().method();
			var requestHandlers = requestHandlerMap.get(requestMethod);
			if (requestHandlers != null) {
				requestHandlers.stream()
					.filter(s -> s.canHandle(sipRequest))
					.findFirst()
					.ifPresentOrElse(
							s -> s.process(normalizerRequest, request.socketConnection()),
							() -> LOGGER.warn("Strategy for request {} not found...", sipRequest));
			} else {
				LOGGER.warn("Ignoring request {}", normalizerRequest);
			}
		}
		else if (sipMessage instanceof SipResponse response) {
			LOGGER.info("Received response {}", response);
			sipResponseHandlers.stream()
					.filter(s -> s.canHandle(response))
					.findFirst()
					.ifPresent(v -> v.process(response, request.socketConnection()));
		}
	}
}
