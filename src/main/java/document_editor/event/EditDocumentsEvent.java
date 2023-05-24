package document_editor.event;

import java.util.List;

import document_editor.dto.Change;

public record EditDocumentsEvent(List<Change> changes) implements DocumentsEvent {
	@Override
	public DocumentsEventType getType() {
		return DocumentsEventType.EDIT;
	}
}
