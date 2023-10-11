package sip.request_handling;

import sip.SipResponse;
import tcp.server.SocketConnection;

public interface SipResponsePostProcessor {
	SipResponse process(SipResponse sipResponse, SocketConnection socketConnection);
}
