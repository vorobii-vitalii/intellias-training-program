package com.example.grpc;

import java.util.function.Supplier;

import com.example.dao.DocumentsDAO;
import com.example.document.storage.*;

import com.mongodb.bulk.BulkWriteResult;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentStorageServiceImpl extends DocumentStorageServiceGrpc.DocumentStorageServiceImplBase {
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
	public void applyChanges(ChangesRequest request, StreamObserver<ChangesResponse> responseObserver) {
		LOGGER.debug("Received request to apply changes");
		var traceContext = contextProvider.get();
		var scope = traceContext.makeCurrent();
		var serverSpan = tracer.spanBuilder("Apply changes")
				.setAttribute("changes.count", request.getChangesCount())
				.setSpanKind(SpanKind.SERVER)
				.startSpan();

		documentsDAO.applyChanges(request).subscribe(new ClosingTracingContextDecorator<>(new Subscriber<>() {
			@Override
			public void onSubscribe(Subscription s) {
				s.request(Long.MAX_VALUE);
			}

			@Override
			public void onNext(BulkWriteResult bulkWriteResult) {

			}

			@Override
			public void onError(Throwable t) {
				serverSpan.recordException(t);
				responseObserver.onError(t);
				responseObserver.onCompleted();
				LOGGER.error("Error on apply changes", t);
			}

			@Override
			public void onComplete() {
				LOGGER.info("Inserts complete");
				responseObserver.onNext(ChangesResponse.newBuilder().build());
				responseObserver.onCompleted();
			}
		}, serverSpan, scope));
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
		var traceContext = contextProvider.get();
		var scope = traceContext.makeCurrent();
		var serverSpan = tracer.spanBuilder("Fetch document")
				.setAttribute("documentId", request.getDocumentId())
				.setSpanKind(SpanKind.SERVER)
				.startSpan();

		documentsDAO.fetchDocumentElements(request.getDocumentId())
				.subscribe(new ClosingTracingContextDecorator<>(new Subscriber<>() {
					private Subscription subscription;

					@Override
					public void onSubscribe(Subscription s) {
						LOGGER.info("Subscribed");
						this.subscription = s;
						subscription.request(1L);
					}

					@Override
					public void onNext(DocumentElements documentElements) {
						serverSpan.addEvent("Next batch",
								Attributes.of(AttributeKey.longKey("count"), (long) documentElements.getDocumentElementsCount()));
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
				}, serverSpan, scope));
	}

	private static class ClosingTracingContextDecorator<T> implements Subscriber<T> {
		private final Subscriber<T> delegate;
		private final Span span;
		private final Scope scope;

		public ClosingTracingContextDecorator(Subscriber<T> delegate, Span span, Scope scope) {
			this.delegate = delegate;
			this.span = span;
			this.scope = scope;
		}

		@Override
		public void onSubscribe(Subscription s) {
			delegate.onSubscribe(s);
		}

		@Override
		public void onNext(T t) {
			delegate.onNext(t);
		}

		@Override
		public void onError(Throwable t) {
			span.recordException(t);
			delegate.onError(t);
			span.end();
			scope.close();
		}

		@Override
		public void onComplete() {
			delegate.onComplete();
			span.end();
			scope.close();
		}
	}

}
