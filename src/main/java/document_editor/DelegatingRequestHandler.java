package document_editor;

import java.util.Map;
import java.util.function.BiConsumer;

import document_editor.dto.ClientRequest;
import document_editor.dto.RequestType;
import tcp.server.SocketConnection;

public class DelegatingRequestHandler implements BiConsumer<SocketConnection, ClientRequest> {
	private final Map<RequestType, BiConsumer<ClientRequest, SocketConnection>> requestHandlerByType;

	public DelegatingRequestHandler(Map<RequestType, BiConsumer<ClientRequest, SocketConnection>> requestHandlerByType) {
		this.requestHandlerByType = requestHandlerByType;
	}

	@Override
	public void accept(SocketConnection connection, ClientRequest clientRequest) {
		var handler = requestHandlerByType.get(clientRequest.type());
		if (handler != null) {
			handler.accept(clientRequest, connection);
		}
	}
}
