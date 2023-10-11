package sip.reactor_netty.request_handling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import request_handler.ReactiveMessageHandler;
import reactor.core.publisher.Flux;
import sip.SipMessage;
import sip.SipRequest;
import sip.reactor_netty.WSOutbound;
import sip.reactor_netty.service.impl.ReactiveConferenceSubscribersContext;

public class SubscribeToConferenceUpdatesReactiveSipRequestHandler
		implements ReactiveMessageHandler<String, SipRequest, SipMessage, WSOutbound> {
	private static final Logger LOGGER = LoggerFactory.getLogger(SubscribeToConferenceUpdatesReactiveSipRequestHandler.class);
	private static final String SUBSCRIBE = "SUBSCRIBE";

	private final ReactiveConferenceSubscribersContext reactiveConferenceSubscribersContext;

	public SubscribeToConferenceUpdatesReactiveSipRequestHandler(
			ReactiveConferenceSubscribersContext reactiveConferenceSubscribersContext
	) {
		this.reactiveConferenceSubscribersContext = reactiveConferenceSubscribersContext;
	}

	@Override
	public Flux<? extends SipMessage> handleMessage(SipRequest request, WSOutbound context) {
		return reactiveConferenceSubscribersContext.subscribeToConferenceUpdates(request)
				.doOnComplete(() -> LOGGER.info("Consumer wont receive updates for conference any more..."));
	}

	@Override
	public String getHandledMessageType() {
		return SUBSCRIBE;
	}

	@Override
	public boolean canHandle(SipRequest sipRequest) {
		return sipRequest.headers().getExpires() > 0;
	}
}
