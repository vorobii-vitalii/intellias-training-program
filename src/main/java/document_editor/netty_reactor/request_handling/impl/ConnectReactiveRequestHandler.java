package document_editor.netty_reactor.request_handling.impl;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.example.document.storage.DocumentElements;
import com.example.document.storage.FetchDocumentContentRequest;
import com.example.document.storage.RxDocumentStorageServiceGrpc;

import document_editor.HttpServer;
import document_editor.dto.Change;
import document_editor.dto.Changes;
import document_editor.dto.ClientRequest;
import document_editor.dto.ConnectDocumentReply;
import document_editor.dto.RequestType;
import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import document_editor.netty_reactor.ReactiveDocumentChangesPublisher;
import request_handler.ReactiveMessageHandler;
import reactor.adapter.rxjava.RxJava2Adapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ConnectReactiveRequestHandler implements ReactiveMessageHandler<RequestType, ClientRequest, Response, Object> {
	private final Supplier<Integer> connectionIdProvider;
	private final Supplier<RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub> documentStorageServiceProvider;
	private final ReactiveDocumentChangesPublisher reactiveDocumentChangesPublisher;

	public ConnectReactiveRequestHandler(
			Supplier<Integer> connectionIdProvider,
			Supplier<RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub> documentStorageServiceProvider,
			ReactiveDocumentChangesPublisher reactiveDocumentChangesPublisher
	) {
		this.connectionIdProvider = connectionIdProvider;
		this.documentStorageServiceProvider = documentStorageServiceProvider;
		this.reactiveDocumentChangesPublisher = reactiveDocumentChangesPublisher;
	}

	@Override
	public Flux<Response> handleMessage(ClientRequest clientRequest, Object context) {
		return Mono.just(clientRequest).flatMapMany(request -> Flux.concat(
				Mono.just(new Response(
						ResponseType.ON_CONNECT,
						new ConnectDocumentReply(connectionIdProvider.get())
				)),
				(streamOfDocument(request).concatWith(Mono.just(
						new Response(ResponseType.CHANGES, new Changes(List.of(), true, "snapshot"))
				))).mergeWith(
						reactiveDocumentChangesPublisher
								.listenForChanges(HttpServer.DOCUMENT_ID)
								.map(change -> new Response(ResponseType.CHANGES, new Changes(List.of(change), false, "event"))))
		));
	}

	private Flux<Response> streamOfDocument(ClientRequest clientRequest) {
		var fetchDocumentContentRequest = FetchDocumentContentRequest.newBuilder()
				.setDocumentId(HttpServer.DOCUMENT_ID)
				.setBatchSize(clientRequest.batchSize())
				.build();
		return RxJava2Adapter.flowableToFlux(documentStorageServiceProvider.get().fetchDocumentContent(fetchDocumentContentRequest))
				.map(v -> new Response(
						ResponseType.CHANGES,
						new Changes(computeChanges(v), false, "snapshot")
				));
	}

	private List<Change> computeChanges(DocumentElements documentElements) {
		return documentElements.getDocumentElementsList()
				.stream()
				.map(doc -> new Change(
						doc.getCharId(),
						doc.hasParentCharId() ? doc.getParentCharId() : null,
						doc.getIsRight(),
						doc.getDisambiguator(),
						doc.hasCharacter() ? ((char) doc.getCharacter()) : null
				))
				.collect(Collectors.toList());
	}

	@Override
	public RequestType getHandledMessageType() {
		return RequestType.CONNECT;
	}
}
