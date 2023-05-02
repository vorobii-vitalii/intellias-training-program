package document_editor.event;

import tcp.server.SocketConnection;

public record NewConnectionEvent(SocketConnection connection) implements Event {

	@Override
	public EventType getType() {
		return EventType.CONNECT;
	}
}
