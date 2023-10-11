package sip.request_handling.enricher;

import java.util.stream.Collectors;

import sip.AddressOfRecord;
import sip.ContactSet;
import sip.SipRequest;
import sip.request_handling.Updater;

public class ContactUnknownAttributesRemover implements Updater<SipRequest> {
	@Override
	public SipRequest update(SipRequest sipRequest) {
		var currentContactList = sipRequest.headers().getContactList();
		if (currentContactList instanceof ContactSet contactSet) {
			var fixedContactSet = contactSet.allowedAddressOfRecords()
					.stream()
					.map(AddressOfRecord::removeUnsetParameters)
					.collect(Collectors.toSet());
			sipRequest.headers().setContactList(new ContactSet(fixedContactSet));
		}
		return sipRequest;
	}
}
