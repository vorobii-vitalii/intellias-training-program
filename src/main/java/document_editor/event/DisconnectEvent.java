package document_editor.event;

import tcp.server.SocketConnection;

public record DisconnectEvent(SocketConnection socketConnection) implements Event {

	@Override
	public EventType getType() {
		return EventType.DISCONNECT;
	}
}
