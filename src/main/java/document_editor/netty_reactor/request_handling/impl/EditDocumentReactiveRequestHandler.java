package document_editor.netty_reactor.request_handling.impl;

import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.document.storage.Change;
import com.example.document.storage.ChangesRequest;
import com.example.document.storage.ChangesResponse;
import com.example.document.storage.RxDocumentStorageServiceGrpc;

import document_editor.HttpServer;
import document_editor.dto.ClientRequest;
import document_editor.dto.RequestType;
import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import document_editor.netty_reactor.request_handling.ReactiveMessageHandler;
import io.reactivex.Single;
import reactor.adapter.rxjava.RxJava2Adapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class EditDocumentReactiveRequestHandler implements ReactiveMessageHandler<RequestType, ClientRequest, Response, Object> {
	private static final Logger LOGGER = LoggerFactory.getLogger(EditDocumentReactiveRequestHandler.class);
	private final Supplier<RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub> service;

	public EditDocumentReactiveRequestHandler(Supplier<RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub> service) {
		this.service = service;
	}

	@Override
	public Flux<Response> handleMessage(ClientRequest clientRequest, Object context) {
		return Mono.just(clientRequest).flatMapMany(request -> {
			var changes = request.payload().stream()
					.map(this::calculateChanges)
					.toList();
			LOGGER.info("Applying changes {}", changes);
			return RxJava2Adapter.singleToMono(applyChanges(changes))
					.map(List::of)
					.flatMapMany(Flux::fromIterable)
					.map(v -> new Response(ResponseType.ACK, request.changeId()))
					.onErrorReturn(new Response(ResponseType.NACK, request.changeId()));
		});
	}

	private Single<ChangesResponse> applyChanges(List<Change> changes) {
		return Single.just(ChangesRequest.newBuilder().addAllChanges(changes).build())
				.flatMap(v -> service.get().applyChanges(v));
	}

	private Change calculateChanges(document_editor.dto.Change c) {
		var builder = Change.newBuilder()
				.setDocumentId(HttpServer.DOCUMENT_ID)
				.setCharId(c.charId())
				.setIsRight(c.isRight())
				.setDisambiguator(c.disambiguator());
		if (c.parentCharId() != null) {
			builder.setParentCharId(c.parentCharId());
		}
		if (c.character() != null) {
			builder.setCharacter(c.character());
		}
		return builder.build();
	}


	@Override
	public RequestType getHandledMessageType() {
		return RequestType.CHANGES;
	}
}
