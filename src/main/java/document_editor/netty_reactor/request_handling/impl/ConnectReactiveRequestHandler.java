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
import document_editor.netty_reactor.request_handling.ReactiveMessageHandler;
import reactor.adapter.rxjava.RxJava2Adapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ConnectReactiveRequestHandler implements ReactiveMessageHandler<RequestType, ClientRequest, Response, Object> {
	private final Supplier<Integer> connectionIdProvider;
	private final Supplier<RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub> service;
	private final ReactiveDocumentChangesPublisher reactiveDocumentChangesPublisher;

	public ConnectReactiveRequestHandler(
			Supplier<Integer> connectionIdProvider,
			Supplier<RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub> service,
			ReactiveDocumentChangesPublisher reactiveDocumentChangesPublisher
	) {
		this.connectionIdProvider = connectionIdProvider;
		this.service = service;
		this.reactiveDocumentChangesPublisher = reactiveDocumentChangesPublisher;
	}

	@Override
	public Flux<Response> handleMessage(ClientRequest requestMono, Object context) {
		return Mono.just(requestMono).flatMapMany(request -> Flux.concat(
				Mono.just(new Response(ResponseType.ON_CONNECT, new ConnectDocumentReply(connectionIdProvider.get()))),
				streamOfDocument(request),
				Mono.just(new Response(ResponseType.CHANGES, new Changes(List.of(), true))),
				reactiveDocumentChangesPublisher.listenForChanges(HttpServer.DOCUMENT_ID)
						.map(change -> new Response(ResponseType.CHANGES, new Changes(List.of(change), false)))
		));
	}

	private Flux<Response> streamOfDocument(ClientRequest clientRequest) {
		var fetchDocumentContentRequest = FetchDocumentContentRequest.newBuilder()
				.setDocumentId(HttpServer.DOCUMENT_ID)
				.setBatchSize(clientRequest.batchSize())
				.build();
		return RxJava2Adapter.flowableToFlux(service.get().fetchDocumentContent(fetchDocumentContentRequest))
				.map(v -> new Response(
						ResponseType.CHANGES,
						new Changes(computeChanges(v), false)
				));
	}

	private List<Change> computeChanges(DocumentElements documentElements) {
		return documentElements.getDocumentElementsList()
				.stream()
				.map(doc -> new Change(
						doc.getCharId(),
						doc.getParentCharId(),
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
