package document_editor.event.handler.impl;

import java.util.Collection;

import document_editor.event.Event;
import document_editor.event.EventType;
import document_editor.event.PingEvent;
import document_editor.event.context.EventContext;
import document_editor.event.handler.EventHandler;

public class PingEventHandler implements EventHandler<PingEvent> {
	@Override
	public EventType<PingEvent> getHandledEventType() {
		return EventType.PING;
	}

	@Override
	public void handle(Collection<PingEvent> events, EventContext eventContext) {
		events.stream()
				.map(PingEvent::socketConnection)
				.forEach(eventContext::addOrUpdateConnection);
	}
}
