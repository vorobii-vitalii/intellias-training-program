package document_editor.event;

import java.nio.channels.SelectionKey;
import java.util.HashSet;
import java.util.Set;

import tcp.server.SocketConnection;
import util.Serializable;

public class EventContext {
	private final Set<SocketConnection> connections = new HashSet<>();

	public void addConnection(SocketConnection connection) {
		connections.add(connection);
	}

	public void removeConnection(SocketConnection connection) {
		connections.remove(connection);
	}

	public void broadCastMessage(Serializable message) throws InterruptedException {
		var iterator = connections.iterator();
		while (iterator.hasNext()) {
			var connection = iterator.next();
			try {
				connection.appendResponse(message);
				connection.changeOperation(SelectionKey.OP_WRITE);
			} catch (Exception exception) {
				iterator.remove();
			}
		}
	}


}
