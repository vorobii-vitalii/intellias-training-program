package com.example.grpc;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.dao.DocumentsDAO;
import com.example.document.storage.ChangesRequest;
import com.example.document.storage.ChangesResponse;
import com.example.document.storage.DocumentChangedEvents;
import com.example.document.storage.DocumentElements;
import com.example.document.storage.FetchDocumentContentRequest;
import com.example.document.storage.RxDocumentStorageServiceGrpc;
import com.example.document.storage.SubscribeForDocumentChangesRequest;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.reactivex.Flowable;
import io.reactivex.Single;

public class DocumentStorageServiceImpl extends RxDocumentStorageServiceGrpc.DocumentStorageServiceImplBase {
	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentStorageServiceImpl.class);
	private final DocumentsDAO documentsDAO;
	private final Tracer tracer;
	private final Supplier<Context> contextProvider;

	public DocumentStorageServiceImpl(DocumentsDAO documentsDAO, OpenTelemetry openTelemetry, Supplier<Context> contextProvider) {
		this.documentsDAO = documentsDAO;
		this.contextProvider = contextProvider;
		this.tracer = openTelemetry.getTracer("Document storage service");
	}

	@Override
	public Single<ChangesResponse> applyChanges(Single<ChangesRequest> requestSingle) {
		var traceContext = contextProvider.get();
		var scope = traceContext.makeCurrent();
		var serverSpan = tracer.spanBuilder("Apply changes").setSpanKind(SpanKind.SERVER).startSpan();
		return requestSingle
				.flatMapPublisher(request -> {
					serverSpan.addEvent("Changes count = " + request.getChangesCount());
					LOGGER.debug("Received request to apply changes");
					return documentsDAO.applyChanges(request);
				})
				.lastOrError()
				.map(v -> ChangesResponse.newBuilder().build())
				.doOnDispose(() -> {
					serverSpan.end();
					scope.close();
				});
	}

	@Override
	public Flowable<DocumentChangedEvents> subscribeForDocumentsChanges(SubscribeForDocumentChangesRequest request) {
		return Flowable.fromPublisher(documentsDAO.subscribeToDocumentsChanges(request));
	}

	@Override
	public Flowable<DocumentElements> fetchDocumentContent(FetchDocumentContentRequest request) {
		var traceContext = contextProvider.get();
		var scope = traceContext.makeCurrent();
		var serverSpan = tracer.spanBuilder("Fetch document")
				.setAttribute("documentId", request.getDocumentId())
				.setSpanKind(SpanKind.SERVER)
				.startSpan();

		return Flowable.fromPublisher(documentsDAO.fetchDocumentElements(request.getDocumentId(), request.getBatchSize()))
				.doOnNext(documentElements ->
						serverSpan.addEvent("Next batch",
								Attributes.of(AttributeKey.longKey("count"), (long) documentElements.getDocumentElementsCount())))
				.doOnTerminate(() -> {
					serverSpan.end();
					scope.close();
				});
	}
}
