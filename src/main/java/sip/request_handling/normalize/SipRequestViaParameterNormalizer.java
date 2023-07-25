package sip.request_handling.normalize;

import java.net.InetSocketAddress;

import sip.SipRequest;

public class SipRequestViaParameterNormalizer implements ObjectNormalizer<SipRequest, SipRequestNormalizeContext> {
	private static final String RPORT = "rport";
	public static final String RECEIVED = "received";
	public static final String ALIAS = "alias";

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
		// Alias is only relevant for UDP
		firstVia.parameters().remove(ALIAS);
		firstVia.parameters().put(RECEIVED, address.getHostName());
		firstVia.parameters().remove(RPORT);
		return sipRequest;
	}
}
