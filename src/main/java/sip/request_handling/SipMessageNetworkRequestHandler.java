package sip.request_handling;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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

	private final Map<String, SipMessageHandler<SipRequest>> requestHandlerMap = new HashMap<>();
	private final SipMessageHandler<SipResponse> sipResponseHandler;
	private final Normalizer<SipRequest, SipRequestNormalizeContext> sipRequestNormalizer;

	public SipMessageNetworkRequestHandler(
			Collection<SipRequestHandler> sipMessageHandlers,
			SipMessageHandler<SipResponse> sipResponseHandler,
			Normalizer<SipRequest, SipRequestNormalizeContext> sipRequestNormalizer
	) {
		for (var sipMessageHandler : sipMessageHandlers) {
			for (var handledType : sipMessageHandler.getHandledTypes()) {
				requestHandlerMap.put(handledType, sipMessageHandler);
			}
		}
		this.sipResponseHandler = sipResponseHandler;
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
