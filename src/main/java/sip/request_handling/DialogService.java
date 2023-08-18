package sip.request_handling;

import sip.SipRequest;
import sip.SipResponse;

public interface DialogService {
	SipResponse establishDialog(SipRequest sipRequest, SipSessionDescription sipSessionDescription);
	SipRequest makeDialogRequest(DialogRequest dialogRequest);
}
