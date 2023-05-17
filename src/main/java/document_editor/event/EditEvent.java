package document_editor.event;

import java.util.List;

import document_editor.dto.Change;

public record EditEvent(List<Change> changes) implements Event {
	@Override
	public EventType getType() {
		return EventType.EDIT;
	}
}
