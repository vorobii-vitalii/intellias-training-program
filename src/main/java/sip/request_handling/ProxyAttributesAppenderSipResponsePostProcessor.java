package sip.request_handling;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.jsdp.SDPException;
import net.sourceforge.jsdp.SDPFactory;
import sip.AddressOfRecord;
import sip.ContactSet;
import sip.SipMediaType;
import sip.SipResponse;
import sip.Via;
import tcp.server.SocketConnection;

public class ProxyAttributesAppenderSipResponsePostProcessor implements SipResponsePostProcessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyAttributesAppenderSipResponsePostProcessor.class);
	private final Via serverVia;
	private final Collection<SDPMediaAddressProcessor> sdpMediaAddressProcessors;
	private final AddressOfRecord proxyContact;

	public ProxyAttributesAppenderSipResponsePostProcessor(Via serverVia,
			Collection<SDPMediaAddressProcessor> sdpMediaAddressProcessors, AddressOfRecord proxyContact) {
		this.serverVia = serverVia;
		this.sdpMediaAddressProcessors = sdpMediaAddressProcessors;
		this.proxyContact = proxyContact;
	}

	@Override
	public SipResponse process(SipResponse sipResponse, SocketConnection socketConnection) {
		var sipResponseCopy = sipResponse.replicate();
		var viaList = sipResponseCopy.headers().getViaList();
		LOGGER.info("Response from {}", socketConnection);
		while (!viaList.isEmpty()) {
			var currVia = viaList.pollFirst();
			LOGGER.info("Via = {}", currVia);
			if (currVia.equals(serverVia)) {
				break;
			}
		}
		var body = sipResponse.payload();
		if (body.length > 0) {
			try {
				var sessionDescription = SDPFactory.parseSessionDescription(new String(body, StandardCharsets.UTF_8));
				LOGGER.info("Response with original SDP = {}", sessionDescription);
				for (var sdpMediaAddressProcessor : sdpMediaAddressProcessors) {
					MediaAddressReplacement mediaAddressReplacement = sdpMediaAddressProcessor.replaceAddress(sessionDescription);
					if (mediaAddressReplacement != null) {
						sessionDescription = mediaAddressReplacement.updatedSessionDescription();
					}
				}
				LOGGER.info("Response with updated SDP = {}", sessionDescription);
				body = sessionDescription.toString().getBytes(StandardCharsets.UTF_8);
			}
			catch (SDPException e) {
				throw new RuntimeException(e);
			}
		}

		sipResponseCopy.headers().setContactList(new ContactSet(Set.of(proxyContact)));
		sipResponseCopy.headers().setContentType(new SipMediaType("application", "sdp", Map.of()));
		return new SipResponse(
				sipResponseCopy.responseLine(),
				sipResponseCopy.headers(),
				body
		);
	}

}
