package document_editor.event.handler;

import document_editor.event.DocumentsEvent;
import document_editor.event.DocumentsEventType;
import document_editor.event.context.ClientConnectionsContext;

public interface EventHandler<E extends DocumentsEvent> {
	DocumentsEventType<E> getHandledEventType();
	void handle(E event, ClientConnectionsContext clientConnectionsContext);
}
