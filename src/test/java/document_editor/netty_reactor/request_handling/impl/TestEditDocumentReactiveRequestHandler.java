package document_editor.netty_reactor.request_handling.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.document.storage.ChangesRequest;
import com.example.document.storage.ChangesResponse;
import com.example.document.storage.RxDocumentStorageServiceGrpc;

import document_editor.HttpServer;
import document_editor.dto.Change;
import document_editor.dto.ClientRequest;
import document_editor.dto.RequestType;
import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import io.reactivex.Single;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TestEditDocumentReactiveRequestHandler {
	private static final String CHANGE_ID = "3536363";
	private static final int BATCH_SIZE = 100;
	public static final String CHAR_ID = "252-2352";
	public static final String PARENT_CHAR_ID = "25241425-35";
	public static final boolean IS_RIGHT = true;
	public static final int DISAMBIGUATOR = 12;
	public static final char CHARACTER = 'A';

	@Mock
	Supplier<RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub> serviceProvider;

	@InjectMocks
	EditDocumentReactiveRequestHandler editDocumentReactiveRequestHandler;

	@Mock
	RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub service;

	@Test
	void handleMessageHappyPath() {

		// Given
		var change = new Change(CHAR_ID, PARENT_CHAR_ID, IS_RIGHT, DISAMBIGUATOR, CHARACTER);
		var clientRequest = new ClientRequest(RequestType.CHANGES, List.of(change), CHANGE_ID, BATCH_SIZE);

		when(serviceProvider.get()).thenReturn(service);
		when(service.applyChanges(ChangesRequest.newBuilder()
				.addAllChanges(List.of(com.example.document.storage.Change.newBuilder()
						.setDocumentId(HttpServer.DOCUMENT_ID)
						.setCharId(CHAR_ID)
						.setIsRight(IS_RIGHT)
						.setDisambiguator(DISAMBIGUATOR)
						.setParentCharId(PARENT_CHAR_ID)
						.setCharacter(CHARACTER)
						.build()
				))
				.build())).thenReturn(Single.just(ChangesResponse.newBuilder().build()));

		// When
		var responseFlux = editDocumentReactiveRequestHandler.handleMessage(clientRequest, new Object());

		// Then
		StepVerifier.create(responseFlux)
				.expectNext(new Response(ResponseType.ACK, CHANGE_ID))
				.expectComplete()
				.log()
				.verify();
	}

	@Test
	void handleMessageServiceFailureCase() {

		// Given
		var change = new Change(CHAR_ID, PARENT_CHAR_ID, IS_RIGHT, DISAMBIGUATOR, CHARACTER);
		var clientRequest = new ClientRequest(RequestType.CHANGES, List.of(change), CHANGE_ID, BATCH_SIZE);

		when(serviceProvider.get()).thenReturn(service);
		when(service.applyChanges(ChangesRequest.newBuilder()
				.addAllChanges(List.of(com.example.document.storage.Change.newBuilder()
						.setDocumentId(HttpServer.DOCUMENT_ID)
						.setCharId(CHAR_ID)
						.setIsRight(IS_RIGHT)
						.setDisambiguator(DISAMBIGUATOR)
						.setParentCharId(PARENT_CHAR_ID)
						.setCharacter(CHARACTER)
						.build()
				))
				.build())).thenReturn(Single.error(new RuntimeException()));

		// When
		var responseFlux = editDocumentReactiveRequestHandler.handleMessage(clientRequest, new Object());

		// Then
		StepVerifier.create(responseFlux)
				.expectNext(new Response(ResponseType.NACK, CHANGE_ID))
				.expectComplete()
				.log()
				.verify();
	}

	@Test
	void getHandledMessageType() {
		assertThat(editDocumentReactiveRequestHandler.getHandledMessageType())
				.isEqualTo(RequestType.CHANGES);
	}
}