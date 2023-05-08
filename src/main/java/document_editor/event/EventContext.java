package document_editor.event;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

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

	public int connections() {
		return connections.size();
	}

	public int numberNotWrittenResponses() {
		int total = 0;
		for (SocketConnection connection : connections) {
			total += connection.getResponsesSize();
		}
		return total;
	}

}
