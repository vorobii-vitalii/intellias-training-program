package request_handler;

import java.util.Objects;

import document_editor.utils.Copyable;
import tcp.server.SocketConnection;

public final class NetworkRequest<Request> implements Copyable<NetworkRequest<Request>> {
	private Request request;
	private SocketConnection socketConnection;

	public void setRequest(Request request) {
		this.request = request;
	}

	public void setSocketConnection(SocketConnection socketConnection) {
		this.socketConnection = socketConnection;
	}

	public NetworkRequest() {
	}

	public NetworkRequest(Request request, SocketConnection socketConnection) {
		this.request = request;
		this.socketConnection = socketConnection;
	}

	public Request request() {
		return request;
	}

	public SocketConnection socketConnection() {
		return socketConnection;
	}

	@SuppressWarnings("unchecked")
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
				Objects.equals(this.socketConnection, that.socketConnection);
	}

	@Override
	public int hashCode() {
		return Objects.hash(request, socketConnection);
	}

	@Override
	public String toString() {
		return "NetworkRequest[" +
				"request=" + request + ", " +
				"socketConnection=" + socketConnection + ']';
	}

	@Override
	public void copy(NetworkRequest<Request> obj) {
		this.setRequest(obj.request());
		this.setSocketConnection(obj.socketConnection());
	}
}
