package document_editor.event.handler;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import document_editor.DocumentStreamingWebSocketEndpoint;
import document_editor.event.EditEvent;
import document_editor.event.Event;
import document_editor.event.EventContext;
import document_editor.event.EventHandler;
import document_editor.event.EventType;
import document_editor.mongo.MongoReactiveAtomBuffer;
import io.micrometer.core.instrument.Timer;

public class EditEventHandler implements EventHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(EditEventHandler.class);

	private final MongoReactiveAtomBuffer mongoReactiveAtomBuffer;
	private final Timer timer;

	public EditEventHandler(MongoReactiveAtomBuffer mongoReactiveAtomBuffer, Timer timer) {
		this.mongoReactiveAtomBuffer = mongoReactiveAtomBuffer;
		this.timer = timer;
	}

	@Override
	public EventType getHandledEventType() {
		return EventType.EDIT;
	}

	@Override
	public void handle(Collection<Event> events, EventContext eventContext) {
		var changes = events.stream()
				.map(event -> (EditEvent) event)
				.flatMap(event -> event.changes().stream())
				.collect(Collectors.toList());
		LOGGER.info("Applying changes {}", changes);
		timer.record(() -> mongoReactiveAtomBuffer.applyChangesBulk(changes));
	}
}
