package sip.request_handling.enricher;

import sip.SipRequest;
import sip.Via;
import sip.request_handling.Updater;

public class ProxyViaSipRequestUpdater implements Updater<SipRequest> {
	private final Via proxyVia;

	public ProxyViaSipRequestUpdater(Via proxyVia) {
		this.proxyVia = proxyVia;
	}

	@Override
	public SipRequest update(SipRequest sipRequest) {
		sipRequest.headers().addViaFront(proxyVia);
		return sipRequest;
	}
}
