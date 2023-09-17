package document_editor.netty_reactor.request_handling.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.document.storage.DocumentElement;
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
import io.reactivex.Flowable;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TestConnectReactiveRequestHandler {
	private static final int CONNECTION_ID = 123;
	private static final int BATCH_SIZE = 100;
	private static final String CHAR_ID = "charId";
	private static final boolean DIRECTION = false;
	private static final int DISAMBIGUATOR = 2;
	private static final char CHARACTER = 'a';

	@Mock
	Supplier<Integer> connectionIdProvider;
	@Mock
	Supplier<RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub> serviceSupplier;
	@Mock
	ReactiveDocumentChangesPublisher reactiveDocumentChangesPublisher;
	ConnectReactiveRequestHandler connectReactiveRequestHandler;
	@Mock
	RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub documentStorageServiceStub;

	@BeforeEach
	void init() {
		connectReactiveRequestHandler = new ConnectReactiveRequestHandler(
				connectionIdProvider,
				serviceSupplier,
				reactiveDocumentChangesPublisher
		);
	}

	@Test
	void handleMessage() {
		// Given
		var clientRequest = new ClientRequest(RequestType.CONNECT, null, null, BATCH_SIZE);
		when(connectionIdProvider.get()).thenReturn(CONNECTION_ID);
		when(serviceSupplier.get()).thenReturn(documentStorageServiceStub);
		when(documentStorageServiceStub.fetchDocumentContent(FetchDocumentContentRequest.newBuilder()
				.setBatchSize(BATCH_SIZE)
				.setDocumentId(HttpServer.DOCUMENT_ID)
				.build()))
				.thenReturn(Flowable.just(DocumentElements.newBuilder()
								.addDocumentElements(DocumentElement.newBuilder()
										.setCharacter(CHARACTER)
										.setDisambiguator(DISAMBIGUATOR)
										.setIsRight(DIRECTION)
										.setCharId(CHAR_ID)
										.build())
						.build()));
		when(reactiveDocumentChangesPublisher.listenForChanges(HttpServer.DOCUMENT_ID)).thenReturn(Flux.just(
				new Change(
						CHAR_ID,
						null,
						DIRECTION,
						DISAMBIGUATOR,
						null
				)
		));

		// When
		var responseFlux = connectReactiveRequestHandler.handleMessage(clientRequest, new Object());

		// Then
		StepVerifier.create(responseFlux)
				.expectNext(new Response(ResponseType.ON_CONNECT, new ConnectDocumentReply(CONNECTION_ID)))
				.expectNext(new Response(
						ResponseType.CHANGES,
						new Changes(List.of(
								new Change(
										CHAR_ID,
										null,
										DIRECTION,
										DISAMBIGUATOR,
										CHARACTER
								)
						),false, "snapshot")
				))
				.expectNext(new Response(ResponseType.CHANGES, new Changes(List.of(), true, "snapshot")))
				.expectNext(new Response(ResponseType.CHANGES, new Changes(List.of(
						new Change(
								CHAR_ID,
								null,
								DIRECTION,
								DISAMBIGUATOR,
								null
						)
				), false, "event")))
				.expectComplete()
				.log()
				.verify();
	}

	@Test
	void getHandledMessageType() {
		assertThat(connectReactiveRequestHandler.getHandledMessageType()).isEqualTo(RequestType.CONNECT);
	}
}