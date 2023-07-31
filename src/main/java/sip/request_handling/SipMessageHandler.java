package sip.request_handling;

import sip.SipMessage;
import tcp.server.SocketConnection;

public interface SipMessageHandler<T extends SipMessage> {
	void process(T message, SocketConnection socketConnection);

	default boolean canHandle(T msg) {
		return true;
	}
}
