package document_editor.event.handler.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.document.storage.ChangesRequest;
import com.example.document.storage.DocumentStorageServiceGrpc;

import document_editor.HttpServer;
import document_editor.dto.Change;
import document_editor.dto.TreePathDTO;
import document_editor.event.EditEvent;
import document_editor.event.EventType;
import document_editor.event.context.EventContext;
import grpc.ServiceDecorator;
import io.opentelemetry.api.trace.TracerProvider;

@ExtendWith(MockitoExtension.class)
class TestEditEventHandler {

	@Mock
	DocumentStorageServiceGrpc.DocumentStorageServiceStub documentStorageServiceStub;
	@Mock
	ServiceDecorator serviceDecorator;
	@Mock
	EventContext eventContext;
	EditEventHandler editEventHandler;

	@BeforeEach
	void setUp() {
		editEventHandler = new EditEventHandler(documentStorageServiceStub, TracerProvider.noop().get("name"), serviceDecorator);
	}

	@Test
	void getHandledEventType() {
		assertThat(editEventHandler.getHandledEventType()).isEqualTo(EventType.EDIT);
	}

	@Test
	void testHandle() {
		var editEvents = List.of(
				new EditEvent(
						List.of(new Change(new TreePathDTO(List.of(true), List.of(1)), 'a'))
				),
				new EditEvent(
						List.of(new Change(new TreePathDTO(List.of(false), List.of(2)), 'b'))
				),
				new EditEvent(
						List.of(new Change(new TreePathDTO(List.of(true, false), List.of(3)), null))
				)
		);
		when(serviceDecorator.decorateService(any())).thenAnswer(i -> i.getArgument(0));
		editEventHandler.handle(editEvents, eventContext);
		verify(documentStorageServiceStub)
				.applyChanges(eq(ChangesRequest.newBuilder()
						.addAllChanges(List.of(
								com.example.document.storage.Change.newBuilder()
										// TODO: Fix when multiple documents are supported
										.setDocumentId(HttpServer.DOCUMENT_ID)
										.addAllDirections(List.of(true))
										.addAllDisambiguators(List.of(1))
										.setCharacter('a')
										.build(),
								com.example.document.storage.Change.newBuilder()
										// TODO: Fix when multiple documents are supported
										.setDocumentId(HttpServer.DOCUMENT_ID)
										.addAllDirections(List.of(false))
										.addAllDisambiguators(List.of(2))
										.setCharacter('b')
										.build(),
								com.example.document.storage.Change.newBuilder()
										// TODO: Fix when multiple documents are supported
										.setDocumentId(HttpServer.DOCUMENT_ID)
										.addAllDirections(List.of(true, false))
										.addAllDisambiguators(List.of(3))
										.build()
						))
						.build()), any());
	}
}