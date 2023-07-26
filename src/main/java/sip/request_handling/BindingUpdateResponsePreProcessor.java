package sip.request_handling;

import sip.SipResponse;
import sip.request_handling.calls.CallsRepository;
import tcp.server.SocketConnection;

public class BindingUpdateResponsePreProcessor implements SipResponsePreProcessor {
	private final CallsRepository callsRepository;

	public BindingUpdateResponsePreProcessor(CallsRepository callsRepository) {
		this.callsRepository = callsRepository;
	}

	@Override
	public SipResponse process(SipResponse sipResponse, SocketConnection socketConnection) {
		var callId = sipResponse.headers().getCallId();
		if (sipResponse.responseLine().sipStatusCode().isErroneous()) {
			callsRepository.remove(callId);
		} else {
			var callDetails = callsRepository.upsert(callId);
			callDetails.addConnection(socketConnection);
			callsRepository.update(callId, callDetails);
		}
		return sipResponse;
	}
}
