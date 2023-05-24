package document_editor;

import java.util.Map;
import java.util.function.BiConsumer;

import document_editor.dto.Request;
import document_editor.dto.RequestType;
import tcp.server.SocketConnection;

public class DelegatingRequestHandler implements BiConsumer<SocketConnection, Request> {
	private final Map<RequestType, BiConsumer<Request, SocketConnection>> requestHandlerByType;

	public DelegatingRequestHandler(Map<RequestType, BiConsumer<Request, SocketConnection>> requestHandlerByType) {
		this.requestHandlerByType = requestHandlerByType;
	}

	@Override
	public void accept(SocketConnection connection, Request request) {
		var handler = requestHandlerByType.get(request.type());
		if (handler != null) {
			handler.accept(request, connection);
		}
	}
}
