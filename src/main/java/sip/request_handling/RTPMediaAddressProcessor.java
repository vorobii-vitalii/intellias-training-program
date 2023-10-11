package sip.request_handling;

import javax.annotation.Nullable;

import net.sourceforge.jsdp.Media;
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
	public MediaAddress getMediaAddress(SessionDescription sessionDescription) throws SDPException {
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
		return new MediaAddress(RTP_MEDIA_FORMAT, new Address(previousIP, port));
	}

	@Override
	public void update(SessionDescription sessionDescription) throws SDPException {
		var mediaDescriptions = sessionDescription.getMediaDescriptions();
		var mainConnection = sessionDescription.getConnection();
		mainConnection.setAddress(proxyAddress.host());
		for (var mediaDescription : mediaDescriptions) {
			var media = mediaDescription.getMedia();
			if (isRTP(media)) {
				media.setPort(proxyAddress.port());
				break;
			}
		}
	}

	private boolean isRTP(Media media) {
		return media.getProtocol().equals(RTP_MEDIA_FORMAT);
	}

}
