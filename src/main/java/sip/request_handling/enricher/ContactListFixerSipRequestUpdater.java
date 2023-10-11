package sip.request_handling.enricher;

import java.util.function.Supplier;

import sip.ContactSet;
import sip.SipRequest;
import sip.request_handling.Updater;

public class ContactListFixerSipRequestUpdater implements Updater<SipRequest> {
	private final Supplier<ContactSet> proxyContactSetSupplier;

	public ContactListFixerSipRequestUpdater(Supplier<ContactSet> proxyContactSetSupplier) {
		this.proxyContactSetSupplier = proxyContactSetSupplier;
	}

	@Override
	public SipRequest update(SipRequest sipRequest) {
		sipRequest.headers().setContactList(proxyContactSetSupplier.get());
		return sipRequest;
	}
}
