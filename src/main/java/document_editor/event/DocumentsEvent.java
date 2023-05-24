package document_editor.event;

public sealed interface DocumentsEvent
		permits EditDocumentsEvent, MessageDistributeDocumentsEvent, NewConnectionDocumentsEvent, PingDocumentsEvent, SendPongsDocumentsEvent {
	DocumentsEventType<?> getType();
}
