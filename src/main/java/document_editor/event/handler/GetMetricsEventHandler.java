package document_editor.event.handler;

import java.util.Collection;

import document_editor.event.Event;
import document_editor.event.EventContext;
import document_editor.event.EventHandler;
import document_editor.event.EventType;
import io.micrometer.core.instrument.Gauge;

public class GetMetricsEventHandler implements EventHandler {

	private final Gauge connectionsCount;
	private final Gauge responsesGauge;

	public GetMetricsEventHandler(Gauge connectionsCount, Gauge responsesGauge) {
		this.connectionsCount = connectionsCount;
		this.responsesGauge = responsesGauge;
	}

	@Override
	public EventType getHandledEventType() {
		return EventType.GET_METRICS;
	}

	@Override
	public void handle(Collection<Event> event, EventContext eventContext) {
		connectionsCount.measure();
		responsesGauge.measure();
	}
}
