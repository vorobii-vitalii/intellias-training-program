package sip.request_handling;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import request_handler.NetworkRequest;
import request_handler.RequestHandler;
import sip.SipMessage;
import sip.SipRequest;
import sip.SipResponse;

public class SipRequestMessageHandler implements RequestHandler<NetworkRequest<SipMessage>> {
	private static final Logger LOGGER = LoggerFactory.getLogger(SipRequestMessageHandler.class);

	private final Map<String, SipMessageHandler<SipRequest>> requestHandlerMap;
	private final SipMessageHandler<SipResponse> sipResponseHandler;

	public SipRequestMessageHandler(
			Collection<SipRequestHandler> sipMessageHandlers,
			SipMessageHandler<SipResponse> sipResponseHandler
	) {
		requestHandlerMap = sipMessageHandlers.stream()
				.collect(Collectors.toMap(SipRequestHandler::getHandledType, v -> v));
		this.sipResponseHandler = sipResponseHandler;
	}

	@Override
	public void handle(NetworkRequest<SipMessage> request) {
		var sipMessage = request.request();
		if (sipMessage instanceof SipRequest sipRequest) {
			LOGGER.info("Received request {}", sipRequest);
			var requestMethod = sipRequest.requestLine().method();
			var requestHandler = requestHandlerMap.get(requestMethod);
			if (requestHandler != null) {
				requestHandler.process(sipRequest, request.socketConnection());
			} else {
				LOGGER.warn("Ignoring request {}", sipRequest);
			}
		}
		else if (sipMessage instanceof SipResponse response) {
			LOGGER.info("Received response {}", response);
			sipResponseHandler.process(response, request.socketConnection());
		}
	}
}
