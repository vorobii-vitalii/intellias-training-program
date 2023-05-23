package document_editor.event.handler.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import document_editor.event.EventType;
import document_editor.event.SendPongsEvent;
import document_editor.event.context.EventContext;
import document_editor.event.handler.EventHandler;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

public class PongEventHandler implements EventHandler<SendPongsEvent> {
	private static final Logger LOGGER = LoggerFactory.getLogger(PongEventHandler.class);

	public static final Response PONG_RESPONSE = new Response(ResponseType.PONG, null);
	private final ObjectMapper objectMapper;

	public PongEventHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public EventType<SendPongsEvent> getHandledEventType() {
		return EventType.SEND_PONGS;
	}

	private byte[] serialize(Object obj) throws IOException {
		var arrayOutputStream = new ByteArrayOutputStream();
		objectMapper.writeValue(new GZIPOutputStream(arrayOutputStream), obj);
		return arrayOutputStream.toByteArray();
	}

	@Override
	public void handle(Collection<SendPongsEvent> events, EventContext eventContext) {
		try {
			eventContext.removeDisconnectedClients();
			var webSocketMessage = new WebSocketMessage();
			webSocketMessage.setFin(true);
			webSocketMessage.setOpCode(OpCode.BINARY);
			webSocketMessage.setPayload(serialize(PONG_RESPONSE));
			eventContext.broadCastMessage(webSocketMessage);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}

	}
}
