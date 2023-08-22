package document_editor.netty_reactor.request_handling;

import reactor.core.publisher.Flux;

public interface ReactiveMessageHandler<MsgType, InputMsg, OutputMsg, Ctx> {
	Flux<? extends OutputMsg> handleMessage(InputMsg message, Ctx context);
	MsgType getHandledMessageType();

	default boolean canHandle(InputMsg inputMsg) {
		return true;
	}
}
