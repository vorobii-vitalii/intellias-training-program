package sip.request_handling;

import java.util.Collection;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sip.SipResponse;
import sip.request_handling.calls.CallsRepository;
import tcp.MessageSerializer;
import tcp.server.OperationType;
import tcp.server.SocketConnection;

public class SipResponseHandler implements SipMessageHandler<SipResponse> {
	private static final Logger LOGGER = LoggerFactory.getLogger(SipResponseHandler.class);
	private final Collection<SipResponsePostProcessor> sipResponsePostProcessors;
	private final MessageSerializer messageSerializer;
	private final CallsRepository callsRepository;

	public SipResponseHandler(
			Collection<SipResponsePostProcessor> sipResponsePostProcessors,
			MessageSerializer messageSerializer,
			CallsRepository callsRepository
	) {
		this.sipResponsePostProcessors = sipResponsePostProcessors;
		this.messageSerializer = messageSerializer;
		this.callsRepository = callsRepository;
	}

	@Override
	public void process(SipResponse sipResponse, SocketConnection socketConnection) {
		var callId = sipResponse.headers().getCallId();
		var connections = callsRepository.upsert(callId).connectionsInvolved();
		var resultResponse = sipResponsePostProcessors.stream()
				.reduce(
						sipResponse,
						(currentResponse, sipResponsePostProcessor) -> sipResponsePostProcessor.process(currentResponse, socketConnection),
						(a, b) -> b
				);
		LOGGER.info("Call id = {} response from {} connections = {}", callId, socketConnection, connections);
		for (var connection : connections) {
			if (!connection.equals(socketConnection)) {
				LOGGER.info("Written to {}", connection);
				connection.appendResponse(messageSerializer.serialize(resultResponse));
				connection.changeOperation(OperationType.WRITE);
			}
		}
		// Get socket connection of another client
//		socketConnection.appendResponse(messageSerializer.serialize(resultResponse));
//		socketConnection.changeOperation(OperationType.WRITE);
	}
}
