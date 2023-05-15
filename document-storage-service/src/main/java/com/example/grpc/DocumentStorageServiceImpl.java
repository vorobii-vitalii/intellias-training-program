package com.example.grpc;

import com.example.dao.DocumentsDAO;
import com.example.document.storage.*;
import io.grpc.stub.StreamObserver;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentStorageServiceImpl extends DocumentStorageServiceGrpc.DocumentStorageServiceImplBase {
	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentStorageServiceImpl.class);
	private final DocumentsDAO documentsDAO;

	public DocumentStorageServiceImpl(DocumentsDAO documentsDAO) {
		this.documentsDAO = documentsDAO;
	}

	@Override
	public void applyChanges(ChangesRequest request, StreamObserver<ChangesResponse> responseObserver) {
		LOGGER.debug("Received request to apply changes");
		documentsDAO.applyChanges(request);
		responseObserver.onNext(ChangesResponse.newBuilder().build());
		responseObserver.onCompleted();
	}

	@Override
	public void subscribeForDocumentsChanges(
			SubscribeForDocumentChangesRequest request,
			StreamObserver<DocumentChangedEvents> responseObserver
	) {
		documentsDAO.subscribeToDocumentsChanges(request).subscribe(new Subscriber<>() {
			Subscription subscription;

			@Override
			public void onSubscribe(Subscription s) {
				LOGGER.info("Subscribed");
				this.subscription = s;
				subscription.request(1L);
			}

			@Override
			public void onNext(DocumentChangedEvents documentChangedEvents) {
				responseObserver.onNext(documentChangedEvents);
				subscription.request(1L);
			}

			@Override
			public void onError(Throwable t) {
				LOGGER.info("Received error", t);
				responseObserver.onError(t);
			}

			@Override
			public void onComplete() {
				LOGGER.info("Completed");
				responseObserver.onCompleted();
			}
		});
	}

	@Override
	public void fetchDocumentContent(FetchDocumentContentRequest request, StreamObserver<DocumentElements> responseObserver) {
		LOGGER.info("Received request to fetch document content {}", request);
		documentsDAO.fetchDocumentElements(request.getDocumentId()).subscribe(new Subscriber<>() {
			private Subscription subscription;

			@Override
			public void onSubscribe(Subscription s) {
				LOGGER.info("Subscribed");
				this.subscription = s;
				subscription.request(1L);
			}

			@Override
			public void onNext(DocumentElements documentElements) {
				responseObserver.onNext(documentElements);
				subscription.request(1L);
			}

			@Override
			public void onError(Throwable t) {
				LOGGER.info("Received error", t);
				responseObserver.onError(t);
			}

			@Override
			public void onComplete() {
				LOGGER.info("Completed");
				responseObserver.onCompleted();
			}
		});
	}
}
