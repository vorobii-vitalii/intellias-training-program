package document_editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import document_editor.event.DocumentsEvent;
import document_editor.event.context.ClientConnectionsContext;
import document_editor.event.handler.EventHandler;
import document_editor.event.DocumentsEventType;

public class PollingDocumentMessageEventsHandler implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(PollingDocumentMessageEventsHandler.class);

	private final Queue<DocumentsEvent> eventsQueue;
	private final ClientConnectionsContext clientConnectionsContext;
	private final Map<DocumentsEventType<?>, EventHandler> eventHandlerMap;

	public PollingDocumentMessageEventsHandler(
			Queue<DocumentsEvent> eventsQueue,
			ClientConnectionsContext clientConnectionsContext,
			List<EventHandler<?>> eventHandlers
	) {
		this.eventsQueue = eventsQueue;
		this.clientConnectionsContext = clientConnectionsContext;
		this.eventHandlerMap = eventHandlers.stream()
				.collect(Collectors.toMap(EventHandler::getHandledEventType, Function.identity()));
	}

	// diagrams
	@Override
	public void run() {
		var eventsMap = new TreeMap<DocumentsEventType<?>, Collection<DocumentsEvent>>();
		var size = eventsQueue.size();
		for (var i = 0; i < size; i++) {
			var event = eventsQueue.poll();
			if (event == null) {
				break;
			}
			eventsMap.compute(event.getType(), (type, curr) -> {
				if (curr != null) {
					curr.add(event);
					return curr;
				}
				return new ArrayList<>(List.of(event));
			});
		}
		LOGGER.debug("Created events map = {}", eventsMap);
//		eventsMap.forEach((type, events) -> {
//			try {
//				var eventHandler = eventHandlerMap.get(type);
//				if (eventHandler != null) {
//					eventHandler.handle(events, clientConnectionsContext);
//				}
//			} catch (Exception error) {
//				LOGGER.error("Error", error);
//			}
//		});
	}
}
