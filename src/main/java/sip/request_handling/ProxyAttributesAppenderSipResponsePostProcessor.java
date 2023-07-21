package sip.request_handling;

import java.util.Map;
import java.util.Set;

import sip.AddressOfRecord;
import sip.ContactSet;
import sip.SipMediaType;
import sip.SipResponse;
import sip.SipURI;
import sip.Via;
import tcp.server.SocketConnection;

public class ProxyAttributesAppenderSipResponsePostProcessor implements SipResponsePostProcessor {
	private final Via proxyVia;
	private final SipURI serverSipURI;
	private final byte[] sdpResponse;

	public ProxyAttributesAppenderSipResponsePostProcessor(Via proxyVia, SipURI serverSipURI, byte[] sdpResponse) {
		this.proxyVia = proxyVia;
		this.serverSipURI = serverSipURI;
		this.sdpResponse = sdpResponse;
	}

	@Override
	public SipResponse process(SipResponse sipResponse, SocketConnection socketConnection) {
		var sipResponseCopy = sipResponse.replicate();
//		sipResponseCopy.headers().setContactList(calculateContactSet(sipResponse));
		sipResponseCopy.headers().addViaAtBeggining(proxyVia);
		sipResponseCopy.headers().setContentType(new SipMediaType("application", "sdp", Map.of()));
//		sipResponseCopy.headers().setTo(sipResponseCopy.headers().getTo()
//				.addParam("tag", UUID.nameUUIDFromBytes(sipResponseCopy.headers().getTo().sipURI().getURIAsString().getBytes()).toString())
//		);
		return new SipResponse(
				sipResponse.responseLine(),
				sipResponse.headers(),
				sdpResponse
		);
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
