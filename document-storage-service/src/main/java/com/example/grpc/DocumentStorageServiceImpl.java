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

import io.opentelemetry.context.Context;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.SingleSource;

public class DocumentStorageServiceImpl extends RxDocumentStorageServiceGrpc.DocumentStorageServiceImplBase {
	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentStorageServiceImpl.class);
	private final DocumentsDAO documentsDAO;

	public DocumentStorageServiceImpl(DocumentsDAO documentsDAO) {
		this.documentsDAO = documentsDAO;
	}

	@Override
	public Single<ChangesResponse> applyChanges(Single<ChangesRequest> requestSingle) {
		return requestSingle
				.flatMapPublisher(request -> {
					LOGGER.debug("Received request to apply changes");
					return documentsDAO.applyChanges(request);
				})
				.lastElement()
				.map(v -> ChangesResponse.newBuilder().build())
				.switchIfEmpty((SingleSource<ChangesResponse>) observer -> observer.onSuccess(ChangesResponse.newBuilder().build()))
				.doOnError(err -> {
					LOGGER.warn("Error", err);
				});
	}

	@Override
	public Flowable<DocumentChangedEvents> subscribeForDocumentsChanges(SubscribeForDocumentChangesRequest request) {
		return Flowable.fromPublisher(documentsDAO.subscribeToDocumentsChanges(request))
				.doOnError(err -> {
					LOGGER.warn("Error", err);
				});
	}

	@Override
	public Flowable<DocumentElements> fetchDocumentContent(FetchDocumentContentRequest request) {
		return Flowable.fromPublisher(documentsDAO.fetchDocumentElements(request.getDocumentId(), request.getBatchSize()))
				.doOnError(err -> LOGGER.warn("Error", err));
	}
}
