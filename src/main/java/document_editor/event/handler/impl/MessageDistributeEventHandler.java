package document_editor.event.handler.impl;

import java.util.Collection;

import document_editor.event.Event;
import document_editor.event.context.EventContext;
import document_editor.event.EventType;
import document_editor.event.MessageDistributeEvent;
import document_editor.event.handler.EventHandler;

public class MessageDistributeEventHandler implements EventHandler<MessageDistributeEvent> {

	@Override
	public EventType<MessageDistributeEvent> getHandledEventType() {
		return EventType.BROADCAST_MESSAGE;
	}

	@Override
	public void handle(Collection<MessageDistributeEvent> events, EventContext eventContext) {
		for (var event : events) {
			try {
				eventContext.broadCastMessage(event.message());
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
