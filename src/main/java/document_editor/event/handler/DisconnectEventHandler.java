package document_editor.event.handler;

import java.util.Collection;

import document_editor.event.DisconnectEvent;
import document_editor.event.Event;
import document_editor.event.EventContext;
import document_editor.event.EventHandler;
import document_editor.event.EventType;

public class DisconnectEventHandler implements EventHandler {

	@Override
	public EventType getHandledEventType() {
		return EventType.DISCONNECT;
	}

	@Override
	public void handle(Collection<Event> events, EventContext eventContext) {
		for (Event event : events) {
			eventContext.removeConnection(((DisconnectEvent) event).socketConnection());
		}
	}
}
