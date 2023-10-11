package request_handler;

import reactor.core.publisher.Flux;

public interface ReactiveMessageHandler<MsgType, InputMsg, OutputMsg, Ctx> {
	Flux<? extends OutputMsg> handleMessage(InputMsg message, Ctx context);
	MsgType getHandledMessageType();

	default boolean canHandle(InputMsg inputMsg) {
		return true;
	}
}
