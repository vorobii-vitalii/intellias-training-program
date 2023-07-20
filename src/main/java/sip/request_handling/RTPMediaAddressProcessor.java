package sip.request_handling;

import java.util.Arrays;

import javax.annotation.Nullable;

import net.sourceforge.jsdp.Media;
import net.sourceforge.jsdp.MediaDescription;
import net.sourceforge.jsdp.SDPException;
import net.sourceforge.jsdp.SessionDescription;
import sip.Address;

public class RTPMediaAddressProcessor implements SDPMediaAddressProcessor {
	private static final String RTP_MEDIA_FORMAT = "RTP/AVP";
	private static final int NOT_FOUND = -1;
	private final Address proxyAddress;

	public RTPMediaAddressProcessor(Address proxyAddress) {
		this.proxyAddress = proxyAddress;
	}

	@Nullable
	@Override
	public MediaAddressReplacement replaceAddress(SessionDescription sessionDescription) throws SDPException {
//		var copySessionDescription = (SessionDescription) sessionDescription.clone();
		var mediaDescriptions = sessionDescription.getMediaDescriptions();
		var mainConnection = sessionDescription.getConnection();
		var previousIP = mainConnection.getAddress();
		var port = NOT_FOUND;
		mainConnection.setAddress(proxyAddress.host());
		for (var mediaDescription : mediaDescriptions) {
			var media = mediaDescription.getMedia();
			if (isRTP(media)) {
				port = media.getPort();
				media.setPort(proxyAddress.port());
				break;
			}
		}
		if (port == NOT_FOUND) {
			return null;
		}
		return new MediaAddressReplacement(
				RTP_MEDIA_FORMAT,
				new Address(previousIP, port),
				proxyAddress,
				sessionDescription
		);
	}

	private boolean isRTP(Media media) {
		return Arrays.asList(media.getMediaFormats()).contains(RTP_MEDIA_FORMAT);
	}

}
