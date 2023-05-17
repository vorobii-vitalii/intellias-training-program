package document_editor.event;

import tcp.server.SocketConnection;

public record PingEvent(SocketConnection socketConnection) implements Event {
	@Override
	public EventType getType() {
		return EventType.PING;
	}
}
