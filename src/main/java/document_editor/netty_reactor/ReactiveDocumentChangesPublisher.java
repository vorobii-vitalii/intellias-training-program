package document_editor.netty_reactor;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.document.storage.DocumentChangedEvent;
import com.example.document.storage.RxDocumentStorageServiceGrpc;
import com.example.document.storage.SubscribeForDocumentChangesRequest;

import document_editor.dto.Change;
import io.reactivex.Single;
import reactor.adapter.rxjava.RxJava2Adapter;
import reactor.core.publisher.Flux;

public class ReactiveDocumentChangesPublisher {
	private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveDocumentChangesPublisher.class);
	private static final int BATCH_SIZE = 100;
	private static final int BATCH_TIMEOUT = 100;

	private final Supplier<RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub> documentStorageService;
	private final Flux<DocumentChangedEvent> documentChangedEventFlux;

	public ReactiveDocumentChangesPublisher(Supplier<RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub> documentStorageService) {
		this.documentStorageService = documentStorageService;
		documentChangedEventFlux = createDocumentChangedEventPublisher(
				SubscribeForDocumentChangesRequest.newBuilder().setBatchSize(BATCH_SIZE).setBatchTimeout(BATCH_TIMEOUT).build())
				.share();
	}

	private Flux<DocumentChangedEvent> createDocumentChangedEventPublisher(SubscribeForDocumentChangesRequest request) {
		AtomicReference<String> lastToken = new AtomicReference<>();
		return RxJava2Adapter.flowableToFlux(
						Single.just(request).flatMapPublisher(v -> documentStorageService.get().subscribeForDocumentsChanges(v)))
				.flatMap(v -> Flux.fromIterable(v.getEventsList()))
				.map(v -> {
					lastToken.set(v.getResumeToken());
					return v;
				})
				.onErrorResume(e -> {
					LOGGER.error("Error occurred when listening to documents changes, retrying...", e);
					return createDocumentChangedEventPublisher(SubscribeForDocumentChangesRequest.newBuilder(request)
							.setResumeToken(lastToken.get())
							.build());
				});
	}

	public Flux<Change> listenForChanges(int documentId) {
		return documentChangedEventFlux
				.filter(e -> e.getChange().getDocumentId() == documentId)
				.map(e -> {
					var change = e.getChange();
					return new Change(
							change.getCharId(),
							change.getParentCharId(),
							change.getIsRight(),
							change.getDisambiguator(),
							change.hasCharacter() ? ((char) change.getCharacter()) : null
					);
				});
	}

}
