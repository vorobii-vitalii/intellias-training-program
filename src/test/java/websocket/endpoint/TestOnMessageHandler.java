package websocket.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import serialization.Deserializer;
import tcp.server.SocketConnection;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

@ExtendWith(MockitoExtension.class)
class TestOnMessageHandler {

	public static final byte[] PAYLOAD = {1, 2, 3};
	public static final ByteArrayInputStream CONTEXT_INPUT_STREAM = new ByteArrayInputStream(new byte[] {4, 5, 6});
	public static final int MESSAGE = 123;
	@Mock
	Deserializer deserializer;

	@Mock
	BiConsumer<SocketConnection, Integer> messageHandler;

	@Mock
	Consumer<IOException> onDeserializationError;

	OnMessageHandler<Integer> onMessageHandler;

	@Mock
	SocketConnection socketConnection;

	@BeforeEach
	void init() {
		onMessageHandler = new OnMessageHandler<>(deserializer, Integer.class, messageHandler, onDeserializationError);
	}

	@Test
	void acceptGivenPartialMessageReceived() {
		var message = new WebSocketMessage();
		message.setFin(false);
		message.setPayload(PAYLOAD);
		message.setOpCode(OpCode.CONTINUATION);
		onMessageHandler.accept(socketConnection, message);
		verify(socketConnection).appendBytesToContext(PAYLOAD);
	}

	@Test
	void acceptGivenFullMessageAndDeserializationError() throws IOException {
		var message = new WebSocketMessage();
		message.setFin(true);
		message.setPayload(PAYLOAD);
		message.setOpCode(OpCode.CONTINUATION);
		when(socketConnection.getContextInputStream()).thenReturn(CONTEXT_INPUT_STREAM);
		var exception = new IOException();
		when(deserializer.deserialize(any(), eq(Integer.class))).thenThrow(exception);
		onMessageHandler.accept(socketConnection, message);
		verify(onDeserializationError).accept(exception);
		verifyNoInteractions(messageHandler);
	}

	@Test
	void acceptGivenFullMessageAndHappyPath() throws IOException {
		var message = new WebSocketMessage();
		message.setFin(true);
		message.setPayload(PAYLOAD);
		message.setOpCode(OpCode.CONTINUATION);
		when(socketConnection.getContextInputStream()).thenReturn(CONTEXT_INPUT_STREAM);
		when(deserializer.deserialize(any(), eq(Integer.class))).thenReturn(MESSAGE);
		onMessageHandler.accept(socketConnection, message);
		verify(messageHandler).accept(socketConnection, MESSAGE);
	}

}