package document_editor.event;

public record GetMetricsEvent() implements Event {

	@Override
	public EventType getType() {
		return EventType.GET_METRICS;
	}
}
