package document_editor.event;

import java.util.Collection;

public interface EventHandler {
	EventType getHandledEventType();
	void handle(Collection<Event> event, EventContext eventContext);
}
