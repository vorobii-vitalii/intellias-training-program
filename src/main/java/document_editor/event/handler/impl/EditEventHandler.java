package document_editor.event.handler.impl;

import java.util.Collection;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.document.storage.Change;
import com.example.document.storage.ChangesRequest;
import com.example.document.storage.ChangesResponse;
import com.example.document.storage.DocumentStorageServiceGrpc;

import document_editor.HttpServer;
import document_editor.event.EditEvent;
import document_editor.event.EventType;
import document_editor.event.context.EventContext;
import document_editor.event.handler.EventHandler;
import grpc.TracingContextPropagator;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

public class EditEventHandler implements EventHandler<EditEvent> {
	private static final Logger LOGGER = LoggerFactory.getLogger(EditEventHandler.class);

	private final DocumentStorageServiceGrpc.DocumentStorageServiceStub service;
	private final Timer timer;
	private final Tracer tracer;
	private final OpenTelemetry openTelemetry;

	public EditEventHandler(DocumentStorageServiceGrpc.DocumentStorageServiceStub service, Timer timer, OpenTelemetry openTelemetry) {
		this.service = service;
		this.timer = timer;
		this.openTelemetry = openTelemetry;
		this.tracer = openTelemetry.getTracer("Edit event handler");
	}

	@Override
	public EventType<EditEvent> getHandledEventType() {
		return EventType.EDIT;
	}

	@Override
	public void handle(Collection<EditEvent> events, EventContext eventContext) {
		var changes = events.stream()
				.flatMap(event -> event.changes().stream())
				.map(c -> {
					var builder = Change.newBuilder()
							.setDocumentId(HttpServer.DOCUMENT_ID)
							.addAllDirections(c.treePath().directions())
							.addAllDisambiguators(c.treePath().disambiguators());
					if (c.character() != null) {
						builder.setCharacter(c.character());
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
		service.withCallCredentials(new TracingContextPropagator(Context.current(), openTelemetry))
				.applyChanges(ChangesRequest.newBuilder().addAllChanges(changes).build(), new StreamObserver<>() {
					@Override
					public void onNext(ChangesResponse changesResponse) {
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

}
