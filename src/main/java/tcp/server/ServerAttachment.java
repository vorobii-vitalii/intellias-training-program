package tcp.server;

import util.Serializable;

import java.util.Map;
import java.util.Queue;


public record ServerAttachment(
				String protocol,
				BufferContext bufferContext,
				Queue<Serializable> responses,
				Map<String, Object> context
) {

}
