package sip.request_handling;

import net.sourceforge.jsdp.MediaDescription;
import net.sourceforge.jsdp.SDPException;
import net.sourceforge.jsdp.SessionDescription;

public class ProxySDPModifier {
	private final String proxyIpAddress;
	private final int proxyPort;

	public ProxySDPModifier(String proxyIpAddress, int proxyPort) {
		this.proxyIpAddress = proxyIpAddress;
		this.proxyPort = proxyPort;
	}

	// TODO: Avoid side effects
	public SessionDescription modify(SessionDescription originalSessionDescription) throws SDPException {
		var mediaDescriptions = originalSessionDescription.getMediaDescriptions();
		var mainConnection = originalSessionDescription.getConnection();
		mainConnection.setAddress(proxyIpAddress);
		for (MediaDescription mediaDescription : mediaDescriptions) {
			mediaDescription.getMedia().setPort(proxyPort);
		}
		return originalSessionDescription;
	}

}
