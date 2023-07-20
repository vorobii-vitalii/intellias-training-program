package sip.request_handling;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.jsdp.SDPException;
import net.sourceforge.jsdp.SDPFactory;
import sip.SipResponse;
import sip.request_handling.calls.CallsRepository;
import tcp.server.SocketConnection;

public class SDPReplacementSipResponsePostProcessor implements SipResponsePostProcessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(SDPReplacementSipResponsePostProcessor.class);

	private final CallsRepository callsRepository;
	private final Collection<SDPMediaAddressProcessor> sdpMediaAddressProcessors;

	public SDPReplacementSipResponsePostProcessor(
			CallsRepository callsRepository,
			Collection<SDPMediaAddressProcessor> sdpMediaAddressProcessors
	) {
		this.callsRepository = callsRepository;
		this.sdpMediaAddressProcessors = sdpMediaAddressProcessors;
	}

	@Override
	public SipResponse process(SipResponse sipResponse, SocketConnection socketConnection) {
		if (sipResponse.isSessionDescriptionProvided()) {
			var callId = sipResponse.headers().getCallId();
			try {
				var callDetails = callsRepository.upsert(callId);
				callDetails.addConnection(socketConnection);
				var sessionDescription = SDPFactory.parseSessionDescription(new String(sipResponse.payload(), StandardCharsets.UTF_8));
				for (var sdpMediaAddressProcessor : sdpMediaAddressProcessors) {
					var mediaAddressReplacement = sdpMediaAddressProcessor.replaceAddress(sessionDescription);
					if (mediaAddressReplacement != null) {
						callDetails.addMediaMapping(mediaAddressReplacement.mediaAddressType(), mediaAddressReplacement.originalAddress());
						sessionDescription = mediaAddressReplacement.updatedSessionDescription();
						LOGGER.info("Did media address replacement {}", mediaAddressReplacement);
					}
				}
				callsRepository.update(callId, callDetails);
				return new SipResponse(
						sipResponse.responseLine(),
						sipResponse.headers(),
						sessionDescription.toString().getBytes(StandardCharsets.UTF_8)
				);
			}
			catch (SDPException e) {
				LOGGER.error("Failed to parse SDP message", e);
				throw new RuntimeException(e);
			}
		}
		return sipResponse;
	}
}
