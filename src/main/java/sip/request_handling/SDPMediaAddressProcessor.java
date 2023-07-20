package sip.request_handling;

import javax.annotation.Nullable;

import net.sourceforge.jsdp.SDPException;
import net.sourceforge.jsdp.SessionDescription;

public interface SDPMediaAddressProcessor {
	@Nullable
	MediaAddressReplacement replaceAddress(SessionDescription sessionDescription) throws SDPException;
}
