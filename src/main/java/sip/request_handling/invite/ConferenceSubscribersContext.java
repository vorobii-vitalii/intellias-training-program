package sip.request_handling.invite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.WebsocketServerSpec;
import serialization.Serializer;
import sip.AddressOfRecord;
import sip.CommandSequence;
import sip.SipMediaType;
import sip.SipRequest;
import sip.SipRequestHeaders;
import sip.SipRequestLine;
import sip.Via;
import tcp.MessageSerializer;
import tcp.server.OperationType;
import tcp.server.SocketConnection;

public class ConferenceSubscribersContext {
	private static final Logger LOGGER = LoggerFactory.getLogger(ConferenceSubscribersContext.class);
	public static final String NOTIFY = "NOTIFY";
	public static final int MAX_FORWARDS = 70;
	public static final String EVENT = "Event";
	public static final String SUBSCRIPTION_STATE = "Subscription-State";
	public static final SipMediaType SIP_MEDIA_TYPE = SipMediaType.parse("application/json");
	public static final String CONFERENCE_EVENT_TYPE = "conference";
	// TODO: OK for now but can be improved (save dialog for each connection)
	private final AtomicLong commandSequence = new AtomicLong(1);

	private final Map<String, Map<AddressOfRecord, Context>> contextMap = new ConcurrentHashMap<>();
	private final MessageSerializer messageSerializer;
	private final MediaConferenceService mediaConferenceService;
	private final Serializer serializer;

	public ConferenceSubscribersContext(MessageSerializer messageSerializer, MediaConferenceService mediaConferenceService,
			Serializer serializer) {
		this.messageSerializer = messageSerializer;
		this.mediaConferenceService = mediaConferenceService;
		this.serializer = serializer;
	}

	public void addSubscriber(String conferenceId, SipRequest sipRequest, SocketConnection socketConnection) {
		final Sinks.Many<Object> objectMany = Sinks.many().multicast().onBackpressureBuffer(100);
		objectMany.emitNext(123, Sinks.EmitFailureHandler.FAIL_FAST);
		objectMany.emitNext(2, Sinks.EmitFailureHandler.busyLooping(Du));
		contextMap.computeIfAbsent(conferenceId, s -> new ConcurrentHashMap<>());
		contextMap.get(conferenceId).put(sipRequest.headers().getFrom(), new Context(socketConnection, sipRequest));
	}

	public void onParticipantsUpdate(String conferenceId) {
		var map = contextMap.get(conferenceId);
		if (map == null) {
			return;
		}
		map.forEach((aor, context) -> {
			var socketConnection = context.socketConnection();
			var request = context.sipRequest();
			var sipRequestHeaders = new SipRequestHeaders();
			for (Via via : request.headers().getViaList()) {
				sipRequestHeaders.addVia(via);
			}
			var tag = UUID.nameUUIDFromBytes(request.headers().getTo().toString().getBytes(StandardCharsets.UTF_8)).toString();
			sipRequestHeaders.setFrom(request.headers().getTo().addParam("tag", tag));
			sipRequestHeaders.setTo(request.headers().getFrom());
			sipRequestHeaders.setCommandSequence(new CommandSequence((int) commandSequence.getAndIncrement(), NOTIFY));
			sipRequestHeaders.setCallId(request.headers().getCallId());
			sipRequestHeaders.setMaxForwards(MAX_FORWARDS);
			sipRequestHeaders.setContactList(request.headers().getContactList());
			sipRequestHeaders.setContentType(SIP_MEDIA_TYPE);
			sipRequestHeaders.addSingleHeader(EVENT, CONFERENCE_EVENT_TYPE);
			sipRequestHeaders.addSingleHeader(SUBSCRIPTION_STATE, "active;expires=240");
			mediaConferenceService.getParticipantsFromPerspectiveOf(conferenceId, request.headers().getFrom().toCanonicalForm().sipURI())
					.subscribeOn(Schedulers.parallel())
					.flatMap(participant -> {
						return participant.iceCandidates()
								.buffer()
								.map(candidates -> ParticipantDTO.create(participant.participantKey(), participant.sdpOffer(), candidates));
					})
					.buffer()
					.subscribe(new BaseSubscriber<>() {

						@Override
						protected void hookOnNext(List<ParticipantDTO> participants) {
							LOGGER.info("Participants from perspective of {} = {}", request.headers().getFrom().toCanonicalForm().sipURI(),
									participants);

							try {
								var participantsSerialized = serializer.serialize(participants);
								sipRequestHeaders.setContentLength(participantsSerialized.length);
								var notifyRequest = new SipRequest(
										new SipRequestLine(NOTIFY, request.headers().getFrom().sipURI(), request.requestLine().version()),
										sipRequestHeaders,
										participantsSerialized
								);
								socketConnection.appendResponse(messageSerializer.serialize(notifyRequest));
								socketConnection.changeOperation(OperationType.WRITE);
							}
							catch (IOException e) {
								throw new RuntimeException(e);
							}
						}
					});
		});
	}

	private record Context(SocketConnection socketConnection, SipRequest sipRequest) {
	}

}
