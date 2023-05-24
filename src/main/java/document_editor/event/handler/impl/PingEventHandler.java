package document_editor.event.handler.impl;

import java.util.Collection;

import document_editor.event.DocumentsEventType;
import document_editor.event.PingDocumentsEvent;
import document_editor.event.context.ClientConnectionsContext;
import document_editor.event.handler.EventHandler;

public class PingEventHandler implements EventHandler<PingDocumentsEvent> {
	@Override
	public DocumentsEventType<PingDocumentsEvent> getHandledEventType() {
		return DocumentsEventType.PING;
	}

	@Override
	public void handle(Collection<PingDocumentsEvent> events, ClientConnectionsContext clientConnectionsContext) {
		events.stream()
				.map(PingDocumentsEvent::socketConnection)
				.forEach(clientConnectionsContext::addOrUpdateConnection);
	}
}
