package document_editor.event.handler.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import document_editor.event.DocumentsEventType;
import document_editor.event.MessageDistributeDocumentsEvent;
import document_editor.event.context.ClientConnectionsContext;
import util.Serializable;

@ExtendWith(MockitoExtension.class)
class TestMessageDistributeDocumentsEventHandler {

	@Mock
	ClientConnectionsContext clientConnectionsContext;

	MessageDistributeEventHandler messageDistributeEventHandler = new MessageDistributeEventHandler();

	@Test
	void getHandledEventType() {
		assertThat(messageDistributeEventHandler.getHandledEventType()).isEqualTo(DocumentsEventType.BROADCAST_MESSAGE);
	}

	@Test
	void handle() {
		var serializable1 = mock(Serializable.class);
		var serializable2 = mock(Serializable.class);

		messageDistributeEventHandler.handle(List.of(
				new MessageDistributeDocumentsEvent(serializable1),
				new MessageDistributeDocumentsEvent(serializable2)
		), clientConnectionsContext);

		verify(clientConnectionsContext).broadCastMessage(serializable1);
		verify(clientConnectionsContext).broadCastMessage(serializable2);
	}
}