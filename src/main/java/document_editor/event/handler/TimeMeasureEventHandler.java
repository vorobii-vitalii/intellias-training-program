package document_editor.event.handler;

import document_editor.event.DocumentsEvent;
import document_editor.event.DocumentsEventType;
import document_editor.event.context.ClientConnectionsContext;
import io.micrometer.core.instrument.Timer;

public class TimeMeasureEventHandler<E extends DocumentsEvent> implements EventHandler<E> {
	private final EventHandler<E> eventHandler;
	private final Timer timer;

	public TimeMeasureEventHandler(EventHandler<E> eventHandler, Timer timer) {
		this.eventHandler = eventHandler;
		this.timer = timer;
	}

	@Override
	public DocumentsEventType<E> getHandledEventType() {
		return eventHandler.getHandledEventType();
	}

	@Override
	public void handle(E event, ClientConnectionsContext clientConnectionsContext) {
		timer.record(() -> eventHandler.handle(event, clientConnectionsContext));
	}
}
