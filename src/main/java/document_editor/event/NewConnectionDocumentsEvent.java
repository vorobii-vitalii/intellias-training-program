package document_editor.event;

import tcp.server.SocketConnection;

public record NewConnectionDocumentsEvent(SocketConnection connection) implements DocumentsEvent {

	@Override
	public DocumentsEventType getType() {
		return DocumentsEventType.CONNECT;
	}
}
