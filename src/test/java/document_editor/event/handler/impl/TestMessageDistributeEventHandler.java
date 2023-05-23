package document_editor.event.handler.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import document_editor.event.EventType;
import document_editor.event.MessageDistributeEvent;
import document_editor.event.context.EventContext;
import util.Serializable;

@ExtendWith(MockitoExtension.class)
class TestMessageDistributeEventHandler {

	@Mock
	EventContext eventContext;

	MessageDistributeEventHandler messageDistributeEventHandler = new MessageDistributeEventHandler();

	@Test
	void getHandledEventType() {
		assertThat(messageDistributeEventHandler.getHandledEventType()).isEqualTo(EventType.BROADCAST_MESSAGE);
	}

	@Test
	void handle() {
		Serializable serializable1 = mock(Serializable.class);
		Serializable serializable2 = mock(Serializable.class);

		messageDistributeEventHandler.handle(List.of(
				new MessageDistributeEvent(serializable1),
				new MessageDistributeEvent(serializable2)
		), eventContext);

		eventContext.broadCastMessage(serializable1);
		eventContext.broadCastMessage(serializable2);
	}
}