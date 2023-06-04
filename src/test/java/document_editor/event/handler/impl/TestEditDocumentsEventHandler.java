package document_editor.event.handler.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.document.storage.DocumentStorageServiceGrpc;

import document_editor.event.DocumentsEventType;
import document_editor.event.context.ClientConnectionsContext;
import grpc.ServiceDecorator;
import io.opentelemetry.api.trace.TracerProvider;
import serialization.Serializer;
import tcp.MessageSerializer;

@ExtendWith(MockitoExtension.class)
class TestEditDocumentsEventHandler {

	@Mock
	DocumentStorageServiceGrpc.DocumentStorageServiceStub documentStorageServiceStub;
	@Mock
	ServiceDecorator serviceDecorator;
	@Mock
	ClientConnectionsContext clientConnectionsContext;
	@Mock
	MessageSerializer messageSerializer;
	@Mock
	Serializer serializer;
	EditEventHandler editEventHandler;

	@BeforeEach
	void setUp() {
		editEventHandler = new EditEventHandler(documentStorageServiceStub, TracerProvider.noop().get("name"), serviceDecorator, messageSerializer, serializer);
	}

	@Test
	void getHandledEventType() {
		assertThat(editEventHandler.getHandledEventType()).isEqualTo(DocumentsEventType.EDIT);
	}

//	@Test
//	void testHandle() {
//		var editEvent = new EditDocumentsEvent(
//				List.of(new Change(new TreePathDTO(List.of(true), List.of(1)), 'a'))
//		);
//		when(serviceDecorator.decorateService(any())).thenAnswer(i -> i.getArgument(0));
//		editEventHandler.handle(editEvent, clientConnectionsContext);
//		verify(documentStorageServiceStub)
//				.applyChanges(eq(ChangesRequest.newBuilder()
//						.addAllChanges(List.of(
//								com.example.document.storage.Change.newBuilder()
//										// TODO: Fix when multiple documents are supported
//										.setDocumentId(HttpServer.DOCUMENT_ID)
//										.addAllDirections(List.of(true))
//										.addAllDisambiguators(List.of(1))
//										.setCharacter('a')
//										.build(),
//								com.example.document.storage.Change.newBuilder()
//										// TODO: Fix when multiple documents are supported
//										.setDocumentId(HttpServer.DOCUMENT_ID)
//										.addAllDirections(List.of(false))
//										.addAllDisambiguators(List.of(2))
//										.setCharacter('b')
//										.build(),
//								com.example.document.storage.Change.newBuilder()
//										// TODO: Fix when multiple documents are supported
//										.setDocumentId(HttpServer.DOCUMENT_ID)
//										.addAllDirections(List.of(true, false))
//										.addAllDisambiguators(List.of(3))
//										.build()
//						))
//						.build()), any());
//	}
}
