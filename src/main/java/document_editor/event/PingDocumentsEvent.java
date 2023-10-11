package document_editor.event;

import tcp.server.SocketConnection;

public record PingDocumentsEvent(SocketConnection socketConnection) implements DocumentsEvent {
	@Override
	public DocumentsEventType getType() {
		return DocumentsEventType.PING;
	}
}
