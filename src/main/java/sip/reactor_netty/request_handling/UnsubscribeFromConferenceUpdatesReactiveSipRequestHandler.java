package sip.reactor_netty.request_handling;

import request_handler.ReactiveMessageHandler;
import reactor.core.publisher.Flux;
import sip.SipMessage;
import sip.SipRequest;
import sip.reactor_netty.WSOutbound;
import sip.reactor_netty.service.impl.ReactiveConferenceSubscribersContext;

public class UnsubscribeFromConferenceUpdatesReactiveSipRequestHandler implements ReactiveMessageHandler<String, SipRequest, SipMessage, WSOutbound> {
	private static final String SUBSCRIBE = "SUBSCRIBE";

	private final ReactiveConferenceSubscribersContext reactiveConferenceSubscribersContext;

	public UnsubscribeFromConferenceUpdatesReactiveSipRequestHandler(
			ReactiveConferenceSubscribersContext reactiveConferenceSubscribersContext
	) {
		this.reactiveConferenceSubscribersContext = reactiveConferenceSubscribersContext;
	}

	@Override
	public Flux<? extends SipMessage> handleMessage(SipRequest request, WSOutbound context) {
		return reactiveConferenceSubscribersContext.unsubscribeFromConferenceUpdates(request);
	}

	@Override
	public String getHandledMessageType() {
		return SUBSCRIBE;
	}

	@Override
	public boolean canHandle(SipRequest sipRequest) {
		return sipRequest.headers().getExpires() == 0;
	}
}