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
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

public class EditEventHandler implements EventHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(EditEventHandler.class);

	private final DocumentStorageServiceGrpc.DocumentStorageServiceStub service;
	private final Timer timer;
	private final OpenTelemetry openTelemetry;
	private final Tracer tracer;

	public EditEventHandler(DocumentStorageServiceGrpc.DocumentStorageServiceStub service, Timer timer, OpenTelemetry openTelemetry) {
		this.service = service;
		this.timer = timer;
		this.openTelemetry = openTelemetry;
		this.tracer = openTelemetry.getTracer("Edit event handler");
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
		var applyChangesSpan = tracer.spanBuilder("Apply documents changes")
				.setSpanKind(SpanKind.CLIENT)
				.setParent(Context.current())
				.startSpan();
		var scope = applyChangesSpan.makeCurrent();
		service.applyChanges(com.example.document.storage.ChangesRequest.newBuilder().addAllChanges(changes).build(), new StreamObserver<>() {
			@Override
			public void onNext(com.example.document.storage.ChangesResponse changesResponse) {
			}

			@Override
			public void onError(Throwable throwable) {
				LOGGER.error("Error on inserts", throwable);
				applyChangesSpan.end();
				scope.close();
			}

			@Override
			public void onCompleted() {
				LOGGER.debug("Operation completed");
				applyChangesSpan.end();
				scope.close();
			}
		});
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
