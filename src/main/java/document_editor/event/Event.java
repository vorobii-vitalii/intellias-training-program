package document_editor.event;

public sealed interface Event permits DisconnectEvent, EditEvent, MessageDistributeEvent, NewConnectionEvent {
	EventType getType();

}
