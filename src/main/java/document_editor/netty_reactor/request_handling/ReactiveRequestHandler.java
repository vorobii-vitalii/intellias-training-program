package document_editor.netty_reactor.request_handling;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReactiveRequestHandler<ReqType, Req, Res, Ctx> {
	Flux<? extends Res> handleRequest(Req request, Ctx context);
	ReqType getHandledRequestType();

	default boolean canHandle(Req canHandle) {
		return true;
	}
}
