package sip.request_handling;

import net.sourceforge.jsdp.SessionDescription;
import sip.Address;

public record MediaAddressReplacement(
		String mediaAddressType,
		Address originalAddress,
		Address updatedAddress,
		SessionDescription updatedSessionDescription
) {
}
