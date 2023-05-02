package document_editor.event;

public sealed interface Event permits DisconnectEvent, GetMetricsEvent, MessageDistributeEvent, NewConnectionEvent  {
	EventType getType();

}
