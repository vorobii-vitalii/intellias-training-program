package document_editor.event;

import util.Serializable;

public record MessageDistributeDocumentsEvent(Serializable message) implements DocumentsEvent {
	@Override
	public DocumentsEventType getType() {
		return DocumentsEventType.BROADCAST_MESSAGE;
	}
}
