package request_handler.impl;

import io.micrometer.core.instrument.Counter;
import reactor.core.publisher.Flux;
import request_handler.ReactiveMessageHandler;

public class CountingReactiveMessageHandler<MsgType, InputMsg, OutputMsg, Ctx>
		implements ReactiveMessageHandler<MsgType, InputMsg, OutputMsg, Ctx> {
	private final ReactiveMessageHandler<MsgType, InputMsg, OutputMsg, Ctx> delegate;
	private final Counter counter;

	public CountingReactiveMessageHandler(
			ReactiveMessageHandler<MsgType, InputMsg, OutputMsg, Ctx> delegate,
			Counter counter
	) {
		this.delegate = delegate;
		this.counter = counter;
	}

	@Override
	public Flux<? extends OutputMsg> handleMessage(InputMsg message, Ctx context) {
		counter.increment();
		return delegate.handleMessage(message, context);
	}

	@Override
	public MsgType getHandledMessageType() {
		return delegate.getHandledMessageType();
	}

	@Override
	public boolean canHandle(InputMsg inputMsg) {
		return delegate.canHandle(inputMsg);
	}
}
