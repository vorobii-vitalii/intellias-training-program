package sip.request_handling;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import sip.AddressOfRecord;
import sip.ContactSet;
import sip.SipResponse;
import sip.SipURI;
import sip.Via;
import tcp.server.SocketConnection;

public class ProxyAttributesAppenderSipResponsePostProcessor implements SipResponsePostProcessor {
	private final Via proxyVia;
	private final SipURI serverSipURI;

	public ProxyAttributesAppenderSipResponsePostProcessor(Via proxyVia, SipURI serverSipURI) {
		this.proxyVia = proxyVia;
		this.serverSipURI = serverSipURI;
	}

	@Override
	public SipResponse process(SipResponse sipResponse, SocketConnection socketConnection) {
		var sipResponseCopy = sipResponse.replicate();
		sipResponseCopy.headers().setContactList(calculateContactSet(sipResponse));
//		sipResponseCopy.headers().addViaAtBeggining(proxyVia);
		sipResponseCopy.headers().setTo(sipResponseCopy.headers().getTo()
				.addParam("tag", UUID.nameUUIDFromBytes(sipResponseCopy.headers().getTo().sipURI().getURIAsString().getBytes()).toString())
		);
		return sipResponseCopy;
	}

	private ContactSet calculateContactSet(SipResponse sipResponse) {
		final AddressOfRecord to = sipResponse.headers().getTo();
		return new ContactSet(Set.of(new AddressOfRecord(
				to.name(),
				serverSipURI,
				Map.of()
		)));
	}

}
