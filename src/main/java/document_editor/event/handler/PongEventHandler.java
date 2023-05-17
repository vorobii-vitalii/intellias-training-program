package document_editor.event.handler;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import document_editor.event.Event;
import document_editor.event.EventHandler;
import document_editor.event.EventType;
import document_editor.event.context.EventContext;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

public class PongEventHandler implements EventHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(PongEventHandler.class);

	public static final Response PONG_RESPONSE = new Response(
			ResponseType.PONG,
			null
	);
	private final ObjectMapper objectMapper;

	public PongEventHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public EventType getHandledEventType() {
		return EventType.SEND_PONGS;
	}

	@Override
	public void handle(Collection<Event> events, EventContext eventContext) {
		try {
			LOGGER.info("Sending PONG to clients");
			eventContext.removeDisconnectedClients();
			var webSocketMessage = new WebSocketMessage();
			webSocketMessage.setFin(true);
			webSocketMessage.setOpCode(OpCode.BINARY);
			webSocketMessage.setPayload(objectMapper.writeValueAsBytes(PONG_RESPONSE));
			eventContext.broadCastMessage(webSocketMessage);
		}
		catch (JsonProcessingException | InterruptedException e) {
			throw new RuntimeException(e);
		}

	}
}
