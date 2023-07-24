package sip.request_handling;

import javax.annotation.Nullable;

import net.sourceforge.jsdp.SDPException;
import net.sourceforge.jsdp.SessionDescription;

public interface SDPMediaAddressProcessor {
	@Nullable
	MediaAddress getMediaAddress(SessionDescription sessionDescription) throws SDPException;
	void update(SessionDescription sessionDescription) throws SDPException;
}
