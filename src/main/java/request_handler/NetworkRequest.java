package request_handler;

import java.util.Objects;

import document_editor.utils.Copyable;
import io.opentelemetry.api.trace.Span;
import tcp.server.SocketConnection;

public final class NetworkRequest<Request> implements Copyable<NetworkRequest<Request>> {
	private Request request;
	private SocketConnection socketConnection;
	private Span span;

	public void setRequest(Request request) {
		this.request = request;
	}

	public void setSocketConnection(SocketConnection socketConnection) {
		this.socketConnection = socketConnection;
	}

	public void setSpan(Span span) {
		this.span = span;
	}

	public NetworkRequest() {
	}

	public NetworkRequest(Request request, SocketConnection socketConnection, Span span) {
		this.request = request;
		this.socketConnection = socketConnection;
		this.span = span;
	}

	public Request request() {
		return request;
	}

	public SocketConnection socketConnection() {
		return socketConnection;
	}

	public Span span() {
		return span;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		var that = (NetworkRequest<Request>) obj;
		return Objects.equals(this.request, that.request) &&
				Objects.equals(this.socketConnection, that.socketConnection) &&
				Objects.equals(this.span, that.span);
	}

	@Override
	public int hashCode() {
		return Objects.hash(request, socketConnection, span);
	}

	@Override
	public String toString() {
		return "NetworkRequest[" +
				"request=" + request + ", " +
				"socketConnection=" + socketConnection + ", " +
				"span=" + span + ']';
	}

	@Override
	public void copy(NetworkRequest<Request> obj) {
		this.setRequest(obj.request());
		this.setSocketConnection(obj.socketConnection());
		this.setSpan(obj.span());
	}
}
