package document_editor.event.handler;

import java.util.Collection;

import document_editor.event.Event;
import document_editor.event.context.EventContext;
import document_editor.event.EventType;
import document_editor.event.MessageDistributeEvent;

public class MessageDistributeEventHandler implements EventHandler {

	@Override
	public EventType getHandledEventType() {
		return EventType.BROADCAST_MESSAGE;
	}

	@Override
	public void handle(Collection<Event> events, EventContext eventContext) {
		for (var event : events) {
			try {
				eventContext.broadCastMessage(((MessageDistributeEvent) event).message());
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
