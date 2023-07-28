package tcp;

import java.nio.ByteBuffer;

import tcp.server.EventEmitter;
import util.Serializable;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

public class WebSocketFramerMessageSerializer implements MessageSerializer {
	private final MessageSerializer messageSerializer;

	public WebSocketFramerMessageSerializer(MessageSerializer messageSerializer) {
		this.messageSerializer = messageSerializer;
	}

	@Override
	public ByteBuffer serialize(Serializable serializable, EventEmitter eventEmitter) {
		var message = new WebSocketMessage();
		message.setOpCode(OpCode.TEXT);
		message.setFin(true);
		var buffer = messageSerializer.serialize(serializable, eventEmitter);
		var payload = new byte[buffer.limit()];
		buffer.get(payload);
		message.setPayload(payload);
		return messageSerializer.serialize(message, eventEmitter);
	}
}
