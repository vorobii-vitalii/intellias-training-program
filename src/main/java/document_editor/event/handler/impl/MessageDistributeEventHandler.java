package document_editor.event.handler.impl;

import java.util.Collection;

import document_editor.event.context.ClientConnectionsContext;
import document_editor.event.DocumentsEventType;
import document_editor.event.MessageDistributeDocumentsEvent;
import document_editor.event.handler.EventHandler;

public class MessageDistributeEventHandler implements EventHandler<MessageDistributeDocumentsEvent> {

	@Override
	public DocumentsEventType<MessageDistributeDocumentsEvent> getHandledEventType() {
		return DocumentsEventType.BROADCAST_MESSAGE;
	}

	@Override
	public void handle(Collection<MessageDistributeDocumentsEvent> events, ClientConnectionsContext clientConnectionsContext) {
		for (var event : events) {
			clientConnectionsContext.broadCastMessage(event.message());
		}
	}
}
