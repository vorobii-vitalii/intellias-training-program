package document_editor.event;

import java.util.List;

import document_editor.DocumentStreamingWebSocketEndpoint;

public record EditEvent(List<DocumentStreamingWebSocketEndpoint.Change> changes) implements Event {
	@Override
	public EventType getType() {
		return EventType.EDIT;
	}
}
