package document_editor.event;

public final class DocumentsEventType<E extends DocumentsEvent> implements Comparable<DocumentsEventType<?>> {
	public static final DocumentsEventType<NewConnectionDocumentsEvent> CONNECT = new DocumentsEventType<>(1);
	public static final DocumentsEventType<MessageDistributeDocumentsEvent> BROADCAST_MESSAGE = new DocumentsEventType<>(0);
	public static final DocumentsEventType<PingDocumentsEvent> PING = new DocumentsEventType<>(4);
	public static final DocumentsEventType<SendPongsDocumentsEvent> SEND_PONGS = new DocumentsEventType<>(2);
	public static final DocumentsEventType<EditDocumentsEvent> EDIT = new DocumentsEventType<>(3);

	private final int priority;

	public DocumentsEventType(int priority) {
		this.priority = priority;
	}

	@Override
	public int compareTo(DocumentsEventType o) {
		return this.priority - o.priority;
	}
}
