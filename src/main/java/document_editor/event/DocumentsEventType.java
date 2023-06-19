package document_editor.event;

public final class DocumentsEventType<E extends DocumentsEvent> implements Comparable<DocumentsEventType<?>> {
	public static final DocumentsEventType<NewConnectionDocumentsEvent> CONNECT = new DocumentsEventType<>(1, "CONNECT");
	public static final DocumentsEventType<MessageDistributeDocumentsEvent> BROADCAST_MESSAGE = new DocumentsEventType<>(0, "BROADCAST_MESSAGE");
	public static final DocumentsEventType<PingDocumentsEvent> PING = new DocumentsEventType<>(4, "PING");
	public static final DocumentsEventType<SendPongsDocumentsEvent> SEND_PONGS = new DocumentsEventType<>(2, "SEND_PONGS");
	public static final DocumentsEventType<EditDocumentsEvent> EDIT = new DocumentsEventType<>(3, "EDIT");

	private final int priority;
	private final String name;

	public DocumentsEventType(int priority, String name) {
		this.priority = priority;
		this.name = name;
	}

	@Override
	public int compareTo(DocumentsEventType o) {
		return this.priority - o.priority;
	}

	public String name() {
		return name;
	}

}
