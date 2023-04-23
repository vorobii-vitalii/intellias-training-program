package tcp.server;

import util.Serializable;

import java.util.Map;
import java.util.Objects;
import java.util.Queue;


public final class ServerAttachment {
	private String protocol;
	private final BufferContext bufferContext;
	private final Queue<Serializable> responses;
	private final Map<String, Object> context;

	public ServerAttachment(
					String protocol,
					BufferContext bufferContext,
					Queue<Serializable> responses,
					Map<String, Object> context
	) {
		this.protocol = protocol;
		this.bufferContext = bufferContext;
		this.responses = responses;
		this.context = context;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public String protocol() {
		return protocol;
	}

	public BufferContext bufferContext() {
		return bufferContext;
	}

	public Queue<Serializable> responses() {
		return responses;
	}

	public Map<String, Object> context() {
		return context;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (ServerAttachment) obj;
		return Objects.equals(this.protocol, that.protocol) &&
						Objects.equals(this.bufferContext, that.bufferContext) &&
						Objects.equals(this.responses, that.responses) &&
						Objects.equals(this.context, that.context);
	}

	@Override
	public int hashCode() {
		return Objects.hash(protocol, bufferContext, responses, context);
	}

	@Override
	public String toString() {
		return "ServerAttachment[" +
						"protocol=" + protocol + ", " +
						"bufferContext=" + bufferContext + ", " +
						"responses=" + responses + ", " +
						"context=" + context + ']';
	}


}
