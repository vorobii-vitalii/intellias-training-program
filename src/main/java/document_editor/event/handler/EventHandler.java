package document_editor.event.handler;

import java.util.Collection;

import document_editor.event.Event;
import document_editor.event.EventType;
import document_editor.event.context.EventContext;

public interface EventHandler {
	EventType getHandledEventType();
	void handle(Collection<Event> events, EventContext eventContext);
}
