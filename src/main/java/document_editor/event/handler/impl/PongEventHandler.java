package document_editor.event.handler.impl;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import document_editor.event.DocumentsEventType;
import document_editor.event.SendPongsDocumentsEvent;
import document_editor.event.context.ClientConnectionsContext;
import document_editor.event.handler.EventHandler;
import serialization.Serializer;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

public class PongEventHandler implements EventHandler<SendPongsDocumentsEvent> {
	private static final Logger LOGGER = LoggerFactory.getLogger(PongEventHandler.class);

	public static final Response PONG_RESPONSE = new Response(ResponseType.PONG, null);
	private final Serializer serializer;

	public PongEventHandler(Serializer serializer) {
		this.serializer = serializer;
	}

	@Override
	public DocumentsEventType<SendPongsDocumentsEvent> getHandledEventType() {
		return DocumentsEventType.SEND_PONGS;
	}

	@Override
	public void handle(SendPongsDocumentsEvent event, ClientConnectionsContext clientConnectionsContext) {
		try {
			clientConnectionsContext.removeDisconnectedClients();
			var webSocketMessage = new WebSocketMessage();
			webSocketMessage.setFin(true);
			webSocketMessage.setOpCode(OpCode.BINARY);
			webSocketMessage.setPayload(serializer.serialize(PONG_RESPONSE));
			clientConnectionsContext.broadCastMessage(webSocketMessage);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
