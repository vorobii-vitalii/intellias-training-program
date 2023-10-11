package sip.request_handling.invite;

import sip.SipRequest;
import sip.SipResponse;

public interface DialogRepository {
	void createDialog(SipRequest sipRequest, SipResponse sipResponse);
}
