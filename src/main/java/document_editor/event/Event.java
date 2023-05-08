package document_editor.event;

public sealed interface Event permits DisconnectEvent, EditEvent, GetMetricsEvent, MessageDistributeEvent, NewConnectionEvent {
	EventType getType();

}
