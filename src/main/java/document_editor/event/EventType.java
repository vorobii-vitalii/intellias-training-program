package document_editor.event;

public final class EventType<E extends Event> implements Comparable<EventType<?>> {
	public static final EventType<NewConnectionEvent> CONNECT = new EventType<>(0);
	public static final EventType<MessageDistributeEvent> BROADCAST_MESSAGE = new EventType<>(1);
	public static final EventType<PingEvent> PING = new EventType<>(2);
	public static final EventType<SendPongsEvent> SEND_PONGS = new EventType<>(3);
	public static final EventType<EditEvent> EDIT = new EventType<>(4);

	private final int priority;

	public EventType(int priority) {
		this.priority = priority;
	}

	@Override
	public int compareTo(EventType o) {
		return this.priority - o.priority;
	}
}
