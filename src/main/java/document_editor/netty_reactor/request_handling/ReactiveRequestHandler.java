package document_editor.netty_reactor.request_handling;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReactiveRequestHandler<ReqType, Req, Res, Ctx> {
	Flux<Res> handleRequest(Mono<Req> request, Ctx context);
	ReqType getHandledRequestType();
}
