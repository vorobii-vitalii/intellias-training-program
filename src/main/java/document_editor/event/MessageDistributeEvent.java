package document_editor.event;

import document_editor.event.Event;
import document_editor.event.EventType;
import util.Serializable;

public record MessageDistributeEvent(Serializable message) implements Event {
	@Override
	public EventType getType() {
		return EventType.BROADCAST_MESSAGE;
	}
}
