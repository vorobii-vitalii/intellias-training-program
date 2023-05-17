package document_editor.event;

public sealed interface Event permits EditEvent, MessageDistributeEvent, NewConnectionEvent, PingEvent, SendPongsEvent {
	EventType getType();

}
