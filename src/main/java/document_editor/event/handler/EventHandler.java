package document_editor.event.handler;

import java.util.Collection;

import document_editor.event.DocumentsEvent;
import document_editor.event.DocumentsEventType;
import document_editor.event.context.ClientConnectionsContext;

public interface EventHandler<E extends DocumentsEvent> {
	DocumentsEventType<E> getHandledEventType();
	void handle(Collection<E> events, ClientConnectionsContext clientConnectionsContext);
}
