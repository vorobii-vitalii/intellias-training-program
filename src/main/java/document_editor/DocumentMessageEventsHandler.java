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

import document_editor.event.Event;
import document_editor.event.context.EventContext;
import document_editor.event.EventHandler;
import document_editor.event.EventType;

public class DocumentMessageEventsHandler implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentMessageEventsHandler.class);

	private final Queue<Event> eventsQueue;
	private final EventContext eventContext;
	private final Map<EventType, EventHandler> eventHandlerMap;

	public DocumentMessageEventsHandler(
			Queue<Event> eventsQueue,
			EventContext eventContext,
			List<EventHandler> eventHandlers
	) {
		this.eventsQueue = eventsQueue;
		this.eventContext = eventContext;
		this.eventHandlerMap = eventHandlers.stream()
				.collect(Collectors.toMap(EventHandler::getHandledEventType, Function.identity()));
	}

	// diagrams
	@Override
	public void run() {
		var eventsMap = new TreeMap<EventType, Collection<Event>>();
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
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Created events map = {}", eventsMap);
		}
		eventsMap.forEach((type, events) -> {
			try {
				var eventHandler = eventHandlerMap.get(type);
				if (eventHandler != null) {
					eventHandler.handle(events, eventContext);
				}
			} catch (Exception error) {
				LOGGER.error("Error", error);
			}
		});
	}
}
