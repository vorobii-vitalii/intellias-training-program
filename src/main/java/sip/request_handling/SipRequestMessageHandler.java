package sip.request_handling;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import request_handler.NetworkRequest;
import request_handler.RequestHandler;
import sip.SipMessage;
import sip.SipRequest;
import sip.SipResponse;
import sip.request_handling.normalize.SipMessageNormalizer;
import sip.request_handling.normalize.SipRequestNormalizeContext;

public class SipRequestMessageHandler implements RequestHandler<NetworkRequest<SipMessage>> {
	private static final Logger LOGGER = LoggerFactory.getLogger(SipRequestMessageHandler.class);

	private final Map<String, SipMessageHandler<SipRequest>> requestHandlerMap = new HashMap<>();
	private final SipMessageHandler<SipResponse> sipResponseHandler;
	private final Collection<SipMessageNormalizer<SipRequest, SipRequestNormalizeContext>> sipRequestsNormalizers;

	public SipRequestMessageHandler(
			Collection<SipRequestHandler> sipMessageHandlers,
			SipMessageHandler<SipResponse> sipResponseHandler,
			Collection<SipMessageNormalizer<SipRequest, SipRequestNormalizeContext>> sipRequestsNormalizers
	) {
		for (var sipMessageHandler : sipMessageHandlers) {
			for (var handledType : sipMessageHandler.getHandledTypes()) {
				requestHandlerMap.put(handledType, sipMessageHandler);
			}
		}
		this.sipResponseHandler = sipResponseHandler;
		this.sipRequestsNormalizers = sipRequestsNormalizers;
	}

	@Override
	public void handle(NetworkRequest<SipMessage> request) {
		var sipMessage = request.request();
		if (sipMessage instanceof SipRequest sipRequest) {
			var context = new SipRequestNormalizeContext(request.socketConnection());
			var normalizerRequest = sipRequestsNormalizers.stream()
					.reduce(sipRequest,
							(prevRequest, normalizer) -> normalizer.normalize(prevRequest, context), (a, b) -> b);
			LOGGER.info("Received request {}", normalizerRequest);
			var requestMethod = normalizerRequest.requestLine().method();
			var requestHandler = requestHandlerMap.get(requestMethod);
			if (requestHandler != null) {
				requestHandler.process(normalizerRequest, request.socketConnection());
			} else {
				LOGGER.warn("Ignoring request {}", normalizerRequest);
			}
		}
		else if (sipMessage instanceof SipResponse response) {
			LOGGER.info("Received response {}", response);
			sipResponseHandler.process(response, request.socketConnection());
		}
	}
}
