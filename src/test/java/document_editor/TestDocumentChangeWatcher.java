package document_editor;

import static document_editor.DocumentChangeWatcher.BATCH_SIZE;
import static document_editor.DocumentChangeWatcher.BATCH_TIMEOUT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

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

import document_editor.dto.Changes;
import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import document_editor.event.DocumentsEvent;
import document_editor.event.MessageDistributeDocumentsEvent;
import io.reactivex.Flowable;
import message_passing.MessageProducer;
import serialization.Serializer;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

@ExtendWith(MockitoExtension.class)
class TestDocumentChangeWatcher {
	private static final byte[] BYTES = {1, 2, 3};

	@Mock
	MessageProducer<DocumentsEvent> eventProducer;

	@Mock
	Serializer serializer;

	@Mock
	RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub documentStorageService;

	@InjectMocks
	DocumentChangeWatcher documentChangeWatcher;

	@Test
	void run() throws IOException {
		var subscribeForDocumentChangesRequest =
				SubscribeForDocumentChangesRequest.newBuilder().setBatchSize(BATCH_SIZE).setBatchTimeout(BATCH_TIMEOUT).build();
		when(documentStorageService.subscribeForDocumentsChanges(subscribeForDocumentChangesRequest))
				.thenReturn(Flowable.just(
						DocumentChangedEvents.newBuilder()
								.addAllEvents(List.of(
										DocumentChangedEvent.newBuilder()
												.setChange(Change.newBuilder()
														.setDocumentId(12)
														.setIsRight(false)
														.setDisambiguator(12)
														.setParentCharId("ABB3525241")
														.setCharId("A3t3225t2525")
														.setCharacter('a')
														.build())
												.setResumeToken("1")
												.build(),
										DocumentChangedEvent.newBuilder()
												.setChange(Change.newBuilder()
														.setDocumentId(15)
														.setIsRight(true)
														.setDisambiguator(14)
														.setParentCharId("ABB3525243")
														.setCharId("A3t3225t25222")
														.setCharacter('b')
														.build())
												.setResumeToken("2")
												.build()
								))
								.build()
				));

		when(serializer.serialize(any())).thenReturn(BYTES);

		documentChangeWatcher.run();

		var webSocketMessage = new WebSocketMessage();
		webSocketMessage.setFin(true);
		webSocketMessage.setOpCode(OpCode.BINARY);
		webSocketMessage.setPayload(BYTES);

		verify(eventProducer).produce(new MessageDistributeDocumentsEvent(webSocketMessage));
		verify(serializer).serialize(new Response(
				ResponseType.CHANGES, new Changes(List.of(

				new document_editor.dto.Change(
						"A3t3225t2525",
						"ABB3525241",
						false,
						12,
						'a'
				),
				new document_editor.dto.Change(
						"A3t3225t25222",
						"ABB3525243",
						true,
						14,
						'b'
				)

		), false)));
		verifyNoMoreInteractions(serializer);
	}

}