package tcp;

import java.nio.ByteBuffer;

import tcp.server.EventEmitter;
import util.Serializable;

public interface MessageSerializer {
	EventEmitter NOOP = e -> {};

	ByteBuffer serialize(Serializable serializable, EventEmitter eventEmitter);

	default ByteBuffer serialize(Serializable serializable) {
		return serialize(serializable, NOOP);
	}

}
