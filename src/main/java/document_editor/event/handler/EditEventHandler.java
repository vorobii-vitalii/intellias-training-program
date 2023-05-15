package document_editor.event.handler;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.example.document.storage.DocumentStorageServiceGrpc;
import document_editor.DocumentStreamingWebSocketEndpoint;
import document_editor.HttpServer;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import document_editor.event.EditEvent;
import document_editor.event.Event;
import document_editor.event.EventContext;
import document_editor.event.EventHandler;
import document_editor.event.EventType;
import io.micrometer.core.instrument.Timer;

public class EditEventHandler implements EventHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(EditEventHandler.class);

	private final DocumentStorageServiceGrpc.DocumentStorageServiceStub service;
	private final Timer timer;

	public EditEventHandler(DocumentStorageServiceGrpc.DocumentStorageServiceStub service, Timer timer) {
		this.service = service;
		this.timer = timer;
	}

	@Override
	public EventType getHandledEventType() {
		return EventType.EDIT;
	}

	@Override
	public void handle(Collection<Event> events, EventContext eventContext) {
		var changes = events.stream()
				.map(event -> (EditEvent) event)
				.flatMap(event -> event.changes().stream())
				.map(c -> {
					var builder = com.example.document.storage.Change.newBuilder()
							.setDocumentId(HttpServer.DOCUMENT_ID)
							.setPath(toTreePath(c.a()));
					if (c.b() != null) {
						builder.setCharacter(c.b());
					}
					return builder.build();
				})
				.collect(Collectors.toList());
		LOGGER.debug("Applying changes {}", changes);
		var responseObserver = new StreamObserver<com.example.document.storage.ChangesResponse>() {
			@Override
			public void onNext(com.example.document.storage.ChangesResponse changesResponse) {
				LOGGER.debug("Response {}", changesResponse);
			}

			@Override
			public void onError(Throwable throwable) {
				LOGGER.debug("Error on inserts", throwable);
			}

			@Override
			public void onCompleted() {
				LOGGER.debug("Operation completed");
			}
		};
		service.applyChanges(com.example.document.storage.ChangesRequest.newBuilder().addAllChanges(changes).build(), responseObserver);
	}

	private com.example.document.storage.TreePath toTreePath(List<DocumentStreamingWebSocketEndpoint.TreePathEntry> entries) {
		return com.example.document.storage.TreePath.newBuilder()
				.addAllEntries(entries.stream()
						.map(entry -> com.example.document.storage.TreePathEntry.newBuilder()
								.setIsLeft(entry.a())
								.setDisambiguator(entry.b())
								.build())
						.collect(Collectors.toList()))
				.build();
	}

}
