package document_editor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import document_editor.event.DocumentsEvent;
import document_editor.event.DocumentsEventType;
import document_editor.event.context.ClientConnectionsContext;
import document_editor.event.handler.EventHandler;
import request_handler.RequestHandler;

public class DelegatingEventHandler implements RequestHandler<DocumentsEvent> {
	private final Map<DocumentsEventType<?>, EventHandler> eventHandlerMap;
	private final ClientConnectionsContext clientConnectionsContext;

	public DelegatingEventHandler(
			ClientConnectionsContext clientConnectionsContext,
			List<EventHandler<?>> eventHandlers
	) {
		this.clientConnectionsContext = clientConnectionsContext;
		this.eventHandlerMap = eventHandlers.stream()
				.collect(Collectors.toMap(EventHandler::getHandledEventType, Function.identity()));
	}

	@Override
	public void handle(DocumentsEvent request) {
		var eventHandler = eventHandlerMap.get(request.getType());
		if (eventHandler != null) {
			eventHandler.handle(request, clientConnectionsContext);
		}
	}
}
