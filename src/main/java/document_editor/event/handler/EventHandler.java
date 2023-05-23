package document_editor.event.handler;

import java.util.Collection;

import document_editor.event.Event;
import document_editor.event.EventType;
import document_editor.event.context.EventContext;

public interface EventHandler<E extends Event> {
	EventType<E> getHandledEventType();
	void handle(Collection<E> events, EventContext eventContext);
}
