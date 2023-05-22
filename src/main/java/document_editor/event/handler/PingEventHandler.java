package document_editor.event.handler;

import java.util.Collection;

import document_editor.event.Event;
import document_editor.event.EventType;
import document_editor.event.PingEvent;
import document_editor.event.context.EventContext;

public class PingEventHandler implements EventHandler {
	@Override
	public EventType getHandledEventType() {
		return EventType.PING;
	}

	@Override
	public void handle(Collection<Event> events, EventContext eventContext) {
		events.stream()
				.map(e -> (PingEvent) e)
				.map(PingEvent::socketConnection)
				.forEach(eventContext::addOrUpdateConnection);
	}
}
