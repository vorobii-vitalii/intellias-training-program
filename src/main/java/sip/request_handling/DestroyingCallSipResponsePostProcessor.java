package sip.request_handling;

import java.util.Set;

import sip.SipResponse;
import sip.request_handling.calls.CallsRepository;
import tcp.server.SocketConnection;

public class DestroyingCallSipResponsePostProcessor implements SipResponsePreProcessor {
	private static final Set<String> CANCELLING_COMMANDS = Set.of("CANCEL", "BYE");

	private final CallsRepository callsRepository;

	public DestroyingCallSipResponsePostProcessor(CallsRepository callsRepository) {
		this.callsRepository = callsRepository;
	}

	@Override
	public SipResponse process(SipResponse sipResponse, SocketConnection socketConnection) {
		var commandSequence = sipResponse.headers().getCommandSequence();
		if (commandSequence != null) {
			if (CANCELLING_COMMANDS.contains(commandSequence.commandName())) {
				var callId = sipResponse.headers().getCallId();
				callsRepository.remove(callId);
			}
		}
		return sipResponse;
	}
}
