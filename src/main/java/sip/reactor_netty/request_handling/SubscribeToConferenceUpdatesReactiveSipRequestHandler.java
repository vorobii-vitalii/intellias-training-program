package sip.reactor_netty.request_handling;

import document_editor.netty_reactor.request_handling.ReactiveMessageHandler;
import reactor.core.publisher.Flux;
import sip.SipMessage;
import sip.SipRequest;
import sip.reactor_netty.WSOutbound;
import sip.reactor_netty.service.ReactiveConferenceSubscribersContext;

public class SubscribeToConferenceUpdatesReactiveSipRequestHandler implements
		ReactiveMessageHandler<String, SipRequest, SipMessage, WSOutbound> {
	private static final String SUBSCRIBE = "SUBSCRIBE";

	private final ReactiveConferenceSubscribersContext reactiveConferenceSubscribersContext;

	public SubscribeToConferenceUpdatesReactiveSipRequestHandler(
			ReactiveConferenceSubscribersContext reactiveConferenceSubscribersContext
	) {
		this.reactiveConferenceSubscribersContext = reactiveConferenceSubscribersContext;
	}

	@Override
	public Flux<? extends SipMessage> handleMessage(SipRequest request, WSOutbound context) {
		return reactiveConferenceSubscribersContext.subscribeToConferenceUpdates(request);
	}

	@Override
	public String getHandledMessageType() {
		return SUBSCRIBE;
	}
}
