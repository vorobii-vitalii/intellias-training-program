package document_editor.netty_reactor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.document.storage.Change;
import com.example.document.storage.DocumentChangedEvent;
import com.example.document.storage.DocumentChangedEvents;
import com.example.document.storage.RxDocumentStorageServiceGrpc;
import com.example.document.storage.SubscribeForDocumentChangesRequest;

import document_editor.HttpServer;
import io.reactivex.Flowable;
import reactor.adapter.rxjava.RxJava2Adapter;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

@ExtendWith(MockitoExtension.class)
class TestReactiveDocumentChangesPublisher {
	private static final int BATCH_SIZE = 100;
	private static final int BATCH_TIMEOUT = 100;
	public static final int DOCUMENT_ID = 123;
	public static final String CHAR_ID = "charId";
	public static final String PARENT_CHAR_ID = "parentCharId";
	public static final char CHARACTER = 'A';
	public static final boolean IS_RIGHT = false;
	public static final int DISAMBIGUATOR = 2;

	@Mock
	Supplier<RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub> documentStorageServiceProvider;

	@InjectMocks
	ReactiveDocumentChangesPublisher reactiveDocumentChangesPublisher;

	@Mock
	RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub documentStorageService;

	@Test
	void listenForChanges() {
		TestPublisher<DocumentChangedEvents> documentChangedEventsPublisher = TestPublisher.createCold();

		when(documentStorageServiceProvider.get()).thenReturn(documentStorageService);

		when(documentStorageService.subscribeForDocumentsChanges(
				SubscribeForDocumentChangesRequest.newBuilder().setBatchSize(BATCH_SIZE).setBatchTimeout(BATCH_TIMEOUT).build()))
				.thenReturn(RxJava2Adapter.fluxToFlowable(documentChangedEventsPublisher.flux()));

		var flux1 = reactiveDocumentChangesPublisher.listenForChanges(DOCUMENT_ID).cache();
		var flux2 = reactiveDocumentChangesPublisher.listenForChanges(DOCUMENT_ID).cache();

		documentChangedEventsPublisher.next(DocumentChangedEvents.newBuilder()
				.addEvents(DocumentChangedEvent.newBuilder()
						.setChange(Change.newBuilder()
								.setCharId(CHAR_ID)
								.setParentCharId(PARENT_CHAR_ID)
								.setCharacter(CHARACTER)
								.setDocumentId(DOCUMENT_ID)
								.setIsRight(IS_RIGHT)
								.setDisambiguator(DISAMBIGUATOR)
								.build())
						.build())
				.build()).complete();

		StepVerifier.create(flux1)
				.expectNext(new document_editor.dto.Change(CHAR_ID, PARENT_CHAR_ID, IS_RIGHT, DISAMBIGUATOR, CHARACTER))
				.expectComplete()
				.log()
				.verify();
		StepVerifier.create(flux2)
				.expectNext(new document_editor.dto.Change(CHAR_ID, PARENT_CHAR_ID, IS_RIGHT, DISAMBIGUATOR, CHARACTER))
				.expectComplete()
				.log()
				.verify();
	}
}