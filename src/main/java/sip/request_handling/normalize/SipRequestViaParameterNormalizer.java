package sip.request_handling.normalize;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

import sip.SipMessage;
import sip.SipRequest;
import sip.Via;
import tcp.server.SocketConnection;

public class SipRequestViaParameterNormalizer implements SipMessageNormalizer<SipRequest, SipRequestNormalizeContext> {
	private static final String RPORT = "rport";

	@Override
	public SipRequest normalize(SipRequest sipRequest, SipRequestNormalizeContext context) {
		var socketConnection = context.socketConnection();
		var viaList = sipRequest.headers().getViaList();
		var firstVia = viaList.get(0);
		var rportValue = firstVia.parameters().get(RPORT);
		// Client doesn't support Extension to the Session Initiation Protocol (SIP) for Symmetric Response Routing, skipping...
		if (rportValue == null) {
			return sipRequest;
		}
		var address = (InetSocketAddress) socketConnection.getAddress();
		// TODO: better to avoid mutable classes...
//		firstVia.parameters().put(RPORT, String.valueOf(address.getPort()));
		firstVia.parameters().put("received", address.getHostName());
		firstVia.parameters().remove("alias");
		firstVia.parameters().remove(RPORT);
		return sipRequest;
	}
}
