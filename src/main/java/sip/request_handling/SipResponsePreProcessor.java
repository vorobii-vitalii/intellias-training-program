package sip.request_handling;

import sip.SipResponse;
import tcp.server.SocketConnection;

public interface SipResponsePreProcessor {
	SipResponse process(SipResponse sipResponse, SocketConnection socketConnection);
}
