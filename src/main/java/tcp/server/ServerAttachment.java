package tcp.server;

import http.HTTPResponse;
import util.Serializable;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;


public record ServerAttachment(
				String protocol,
				ReadBufferContext readBufferContext,
				Queue<Serializable> responses,
				Map<String, Object> context
) {

	public ServerAttachment(
					String protocol,
					ReadBufferContext readBufferContext,
					Serializable response
	) {
		this(protocol, readBufferContext, createQueue(response), Map.of());
	}
	private static Queue<Serializable> createQueue(Serializable serializable) {
		ArrayDeque<Serializable> queue = new ArrayDeque<>();
		if (serializable != null) {
			queue.add(serializable);
		}
		return queue;
	}


}
