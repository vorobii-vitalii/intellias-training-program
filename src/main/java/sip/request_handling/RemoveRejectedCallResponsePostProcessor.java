package sip.request_handling;

import sip.SipResponse;
import sip.request_handling.calls.CallsRepository;
import tcp.server.SocketConnection;

public class RemoveRejectedCallResponsePostProcessor implements SipResponsePostProcessor {
	private final CallsRepository callsRepository;

	public RemoveRejectedCallResponsePostProcessor(CallsRepository callsRepository) {
		this.callsRepository = callsRepository;
	}

	@Override
	public SipResponse process(SipResponse sipResponse, SocketConnection socketConnection) {
		var callId = sipResponse.headers().getCallId();
//		if (sipResponse.responseLine().sipStatusCode().isErroneous()) {
//			callsRepository.remove(callId);
//		}
		return sipResponse;
	}
}
