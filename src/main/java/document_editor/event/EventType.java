package document_editor.event;

public final class EventType<E extends Event> {
	public static final EventType<NewConnectionEvent> CONNECT = new EventType<>();
	public static final EventType<MessageDistributeEvent> BROADCAST_MESSAGE = new EventType<>();
	public static final EventType<PingEvent> PING = new EventType<>();
	public static final EventType<SendPongsEvent> SEND_PONGS = new EventType<>();
	public static final EventType<EditEvent> EDIT = new EventType<>();
}
