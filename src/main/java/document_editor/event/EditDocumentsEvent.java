package document_editor.event;

import java.util.List;

import document_editor.dto.Change;
import tcp.server.SocketConnection;

public record EditDocumentsEvent(List<Change> changes, String changeId, SocketConnection socketConnection) implements DocumentsEvent {
	@Override
	public DocumentsEventType getType() {
		return DocumentsEventType.EDIT;
	}
}
