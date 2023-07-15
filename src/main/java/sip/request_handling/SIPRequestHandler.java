package sip.request_handling;

import sip.SipRequest;
import tcp.server.SocketConnection;

public interface SIPRequestHandler {
	void processRequest(SipRequest sipRequest, SocketConnection socketConnection);
	String getHandledRequestType();
}
