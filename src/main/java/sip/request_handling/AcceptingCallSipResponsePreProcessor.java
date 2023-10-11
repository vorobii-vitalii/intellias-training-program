package sip.request_handling;

import sip.SipResponse;
import sip.request_handling.calls.CallState;
import sip.request_handling.calls.CallsRepository;
import tcp.server.SocketConnection;

public class AcceptingCallSipResponsePreProcessor implements SipResponsePreProcessor {
	private static final String INVITE = "INVITE";
	private final CallsRepository callsRepository;

	public AcceptingCallSipResponsePreProcessor(CallsRepository callsRepository) {
		this.callsRepository = callsRepository;
	}

	@Override
	public SipResponse process(SipResponse sipResponse, SocketConnection socketConnection) {
		var commandSequence = sipResponse.headers().getCommandSequence();
		if (commandSequence != null) {
			if (commandSequence.commandName().equals(INVITE)) {
				var callId = sipResponse.headers().getCallId();
				var callDetails = callsRepository.upsert(callId);
				callsRepository.update(callId, callDetails.changeCallState(CallState.ACCEPTED));
			}
		}
		return sipResponse;
	}
}
