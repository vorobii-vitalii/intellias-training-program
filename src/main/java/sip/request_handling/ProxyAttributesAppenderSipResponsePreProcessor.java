package sip.request_handling;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sip.AddressOfRecord;
import sip.ContactSet;
import sip.SipMediaType;
import sip.SipResponse;
import sip.Via;
import tcp.server.SocketConnection;

public class ProxyAttributesAppenderSipResponsePreProcessor implements SipResponsePreProcessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyAttributesAppenderSipResponsePreProcessor.class);
	private final Via serverVia;
	private final Collection<SDPMediaAddressProcessor> sdpMediaAddressProcessors;
	private final AddressOfRecord proxyContact;

	public ProxyAttributesAppenderSipResponsePreProcessor(Via serverVia,
			Collection<SDPMediaAddressProcessor> sdpMediaAddressProcessors, AddressOfRecord proxyContact) {
		this.serverVia = serverVia;
		this.sdpMediaAddressProcessors = sdpMediaAddressProcessors;
		this.proxyContact = proxyContact;
	}

	@Override
	public SipResponse process(SipResponse sipResponse, SocketConnection socketConnection) {
		var sipResponseCopy = sipResponse.replicate();
		var viaList = sipResponseCopy.headers().getViaList();
		while (!viaList.isEmpty()) {
			var currVia = viaList.pollFirst();
			LOGGER.info("Via = {}", currVia);
			if (currVia.equals(serverVia)) {
				break;
			}
		}
		sipResponseCopy.headers().setContactList(new ContactSet(Set.of(proxyContact)));
		sipResponseCopy.headers().setContentType(new SipMediaType("application", "sdp", Map.of()));
		return sipResponseCopy;
	}
}
