package sip.request_handling;

import sip.SipRequest;
import sip.SipResponse;

public interface DialogService<T> {
	SipResponse establishDialog(SipRequest sipRequest, T data);
	SipRequest makeDialogRequest(DialogRequest dialogRequest);
}
