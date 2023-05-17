package document_editor.event;

public record SendPongsEvent() implements Event {
	@Override
	public EventType getType() {
		return EventType.SEND_PONGS;
	}
}
