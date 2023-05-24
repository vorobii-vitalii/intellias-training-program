package document_editor.event.handler.impl;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.document.storage.Change;
import com.example.document.storage.ChangesRequest;
import com.example.document.storage.ChangesResponse;
import com.example.document.storage.DocumentStorageServiceGrpc;

import document_editor.HttpServer;
import document_editor.event.EditDocumentsEvent;
import document_editor.event.DocumentsEventType;
import document_editor.event.context.ClientConnectionsContext;
import document_editor.event.handler.EventHandler;
import grpc.ServiceDecorator;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

public class EditEventHandler implements EventHandler<EditDocumentsEvent> {
	private static final Logger LOGGER = LoggerFactory.getLogger(EditEventHandler.class);

	private final DocumentStorageServiceGrpc.DocumentStorageServiceStub service;
	private final Tracer tracer;
	private final ServiceDecorator serviceDecorator;

	public EditEventHandler(
			DocumentStorageServiceGrpc.DocumentStorageServiceStub service,
			Tracer tracer,
			ServiceDecorator serviceDecorator
	) {
		this.service = service;
		this.tracer = tracer;
		this.serviceDecorator = serviceDecorator;
	}

	@Override
	public DocumentsEventType<EditDocumentsEvent> getHandledEventType() {
		return DocumentsEventType.EDIT;
	}

	@Override
	public void handle(Collection<EditDocumentsEvent> events, ClientConnectionsContext clientConnectionsContext) {
		var changes = calculateChanges(events);
		LOGGER.debug("Applying changes {}", changes);
		var applyChangesSpan = tracer.spanBuilder("Apply documents changes")
				.setSpanKind(SpanKind.CLIENT)
				.setParent(Context.current())
				.startSpan();
		var scope = applyChangesSpan.makeCurrent();
		serviceDecorator.decorateService(service)
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

	private List<Change> calculateChanges(Collection<EditDocumentsEvent> events) {
		return events.stream()
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
	}

}
