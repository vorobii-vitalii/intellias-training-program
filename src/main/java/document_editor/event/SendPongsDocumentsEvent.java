package document_editor.event;

public record SendPongsDocumentsEvent() implements DocumentsEvent {
	@Override
	public DocumentsEventType getType() {
		return DocumentsEventType.SEND_PONGS;
	}
}
