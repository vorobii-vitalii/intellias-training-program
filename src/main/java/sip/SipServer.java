package sip;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import message_passing.BlockingQueueMessageProducer;
import request_handler.NetworkRequest;
import request_handler.RequestHandler;
import request_handler.RequestProcessor;
import tcp.MessageSerializer;
import tcp.server.ByteBufferPool;
import tcp.server.OperationType;
import tcp.server.SocketConnection;
import tcp.server.SocketMessageReaderImpl;
import tcp.server.TCPServer;
import tcp.server.TCPServerConfig;
import tcp.server.handler.GenericReadOperationHandler;
import tcp.server.handler.WriteOperationHandler;

public class SipServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(SipServer.class);
	public static final int BUFFER_EXPIRATION_TIME_MILLIS = 10_000;
	public static final int REQUEST_QUEUE_CAPACITY = 10_000;
	public static final Via CURRENT_VIA = new Via(new SipSentProtocol("SIP", "2.0", "TCP"),
			new Address(getHost(), getPort()),
			Map.of("branch", "z9hG4bK25235636", "received", "127.0.0.1")
	);

	private static void startProcess(Runnable process, String processName) {
		var thread = new Thread(process);
		thread.setName(processName);
		thread.setUncaughtExceptionHandler((t, e) -> LOGGER.error("Error in thread {}", t, e));
		thread.start();
	}

	private static final ByteBufferPool BUFFER_POOL = new ByteBufferPool(ByteBuffer::allocate, BUFFER_EXPIRATION_TIME_MILLIS);
	private static final MessageSerializer MESSAGE_SERIALIZER = new MessageSerializer(BUFFER_POOL);
	private static final AtomicInteger counter = new AtomicInteger();

	private static final ScheduledExecutorService EXECUTOR_SERVICE = Executors.newScheduledThreadPool(4);

	public static void main(String[] args) {
		var requestQueue = new ArrayBlockingQueue<NetworkRequest<SipMessage>>(REQUEST_QUEUE_CAPACITY);

		var messageSerializer = new MessageSerializer(BUFFER_POOL);

		var meterRegistry = new SimpleMeterRegistry();
		var sipRequestHandleTimer = Timer.builder("sip.request.time").register(meterRegistry);
		var sipWriteTimer = Timer.builder("sip.response.write.time").register(meterRegistry);
		var sipRequestCount = Counter.builder("sip.request.count").register(meterRegistry);
		var sipWriteCount = Counter.builder("sip.response.write.count").register(meterRegistry);

		RequestHandler<NetworkRequest<SipMessage>> sipRequestHandler = request -> {
			var sipMessage = request.request();
			LOGGER.info("Received SIP message {} from {}", sipMessage, request.socketConnection());
			if (sipMessage instanceof SipRequest sipRequest) {
				switch (sipRequest.requestLine().method()) {
					case "REGISTER" -> {
						LOGGER.info("Client registered");
						EXECUTOR_SERVICE.schedule(() -> {
							sendInvite(sipRequest, request.socketConnection());
						}, 5000, TimeUnit.MILLISECONDS);
						//					SIP/2.0 200 OK
						//					Via: SIP/2.0/UDP bobspc.biloxi.com:5060;branch=z9hG4bKnashds7
						//					;received=192.0.2.4
						//					To: Bob <sip:bob@biloxi.com>;tag=2493k59kd
						//					From: Bob <sip:bob@biloxi.com>;tag=456248
						//					Call-ID: 843817637684230@998sdasdh09
						//					CSeq: 1826 REGISTER
						//					Contact: <sip:bob@192.0.2.4>
						//							Expires: 7200
						//					Content-Length: 0
						sendOK(sipRequest, request.socketConnection());
					}
					case "INVITE" -> {
						LOGGER.info("Client calling");
						var sipResponseHeaders = new SipResponseHeaders();
						for (Via via : sipRequest.headers().getViaList()) {
							sipResponseHeaders.addVia(via.normalize());
						}
						sipResponseHeaders.addVia(CURRENT_VIA);
						sipResponseHeaders.setFrom(sipRequest.headers().getFrom());
						sipResponseHeaders.setTo(sipRequest.headers().getTo()
								.addParam("tag", UUID.nameUUIDFromBytes(sipRequest.headers().getTo().sipURI().getURIAsString().getBytes()).toString())
						);
						sipResponseHeaders.setContactList(calculateContactSet(sipRequest));
						sipResponseHeaders.setCallId(sipRequest.headers().getCallId());
						sipResponseHeaders.setCommandSequence(sipRequest.headers().getCommandSequence());
						var response = new SipResponse(
								new SipResponseLine(new SipVersion(2, 0), new SipStatusCode(180), "Ringing"),
								sipResponseHeaders,
								new byte[] {}
						);
						request.socketConnection().appendResponse(messageSerializer.serialize(response));
						request.socketConnection().changeOperation(OperationType.WRITE);
						EXECUTOR_SERVICE.schedule(() -> {
							if (counter.getAndIncrement() % 2 == 0) {
								sendOK(sipRequest, request.socketConnection());
							} else {
								sendBusy(sipRequest, request.socketConnection());
							}
						}, 3000, TimeUnit.MILLISECONDS);
					}
				}
			}

		};

		RequestProcessor<NetworkRequest<SipMessage>> requestProcessor =
				new RequestProcessor<>(requestQueue, sipRequestHandler, sipRequestHandleTimer, sipRequestCount);

		startProcess(requestProcessor, "SIP request processor");

		var sipReadHandler = new GenericReadOperationHandler<>(
				new BlockingQueueMessageProducer<>(requestQueue),
				new SocketMessageReaderImpl<>(new SipMessageReader()),
				(selectionKey, error) -> {
					LOGGER.warn("Client error {}", selectionKey, error);
				},
				TracerProvider.noop().get(""),
				Context::current
		);

		var server = new TCPServer(
				TCPServerConfig.builder()
						.setHost(getHost())
						.setPort(getPort())
						.setProtocolFamily(StandardProtocolFamily.INET)
						.onConnectionClose(connection -> {
							LOGGER.info("Connection closed");
						})
						.build(),
				SelectorProvider.provider(),
				System.err::println,
				Map.of(
						OP_ACCEPT, new SipAcceptOperationHandler(BUFFER_POOL),
						OP_READ, sipReadHandler,
						OP_WRITE, new WriteOperationHandler(
								sipWriteTimer,
								sipWriteCount,
								(key, e) -> {
									LOGGER.warn("Error", e);
								},
								BUFFER_POOL,
								OpenTelemetry.noop()
						)
				));
		server.start();
	}


	private static ContactSet calculateContactSet(SipRequest sipRequest) {
		final AddressOfRecord to = sipRequest.headers().getTo();
		//
		return new ContactSet(Set.of(new AddressOfRecord(
				to.name(),
				getCurrentSipURI(),
				Map.of()
		)));
	}

	private static FullSipURI getCurrentSipURI() {
		return new FullSipURI(
				"sip",
				new Credentials(null, null),
				new Address(getHost(), getPort()),
				Map.of("transport", "tcp"),
				Map.of()
		);
	}

	//	INVITE sip:bob@biloxi.com SIP/2.0
//	Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bKnashds8
//	Max-Forwards: 70
//	To: Bob <sip:bob@biloxi.com>
//	From: Alice <sip:alice@atlanta.com>;tag=1928301774
//	Call-ID: a84b4c76e66710
//	CSeq: 314159 INVITE
//	Contact: <sip:alice@pc33.atlanta.com>
//	Content-Type: application/sdp
//	Content-Length: 142

//	SIP/2.0 200 OK
//	Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bKnashds8
//	;received=192.0.2.1
//	To: Bob <sip:bob@biloxi.com>;tag=a6c85cf
//	From: Alice <sip:alice@atlanta.com>;tag=1928301774
//	Call-ID: a84b4c76e66710
//	CSeq: 314159 INVITE
//	Contact: <sip:bob@192.0.2.4>
//	Content-Type: application/sdp
//	Content-Length: 131

//	F1 INVITE Alice -> atlanta.com proxy
//
//	INVITE sip:bob@biloxi.com SIP/2.0
//	Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bKnashds8
//	Max-Forwards: 70
//	To: Bob <sip:bob@biloxi.com>
//	From: Alice <sip:alice@atlanta.com>;tag=1928301774
//	Call-ID: a84b4c76e66710
//	CSeq: 314159 INVITE
//	Contact: <sip:alice@pc33.atlanta.com>
//	Content-Type: application/sdp
//	Content-Length: 142
//
//			(Alice's SDP not shown)
//
//
//
//	F2 100 Trying atlanta.com proxy -> Alice
//
//	SIP/2.0 100 Trying
//	Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bKnashds8
//	;received=192.0.2.1
//	To: Bob <sip:bob@biloxi.com>
//	From: Alice <sip:alice@atlanta.com>;tag=1928301774
//	Call-ID: a84b4c76e66710
//	CSeq: 314159 INVITE
//	Content-Length: 0

	private static void sendBusy(SipRequest sipRequest, SocketConnection socketConnection) {
		var sipResponseHeaders = new SipResponseHeaders();
		for (Via via : sipRequest.headers().getViaList()) {
			sipResponseHeaders.addVia(via.normalize());
		}
		sipResponseHeaders.addVia(CURRENT_VIA);
		sipResponseHeaders.setFrom(sipRequest.headers().getFrom());
		sipResponseHeaders.setTo(sipRequest.headers().getTo()
				.addParam("tag", UUID.nameUUIDFromBytes(sipRequest.headers().getTo().sipURI().getURIAsString().getBytes()).toString())
		);
		sipResponseHeaders.setContactList(calculateContactSet(sipRequest));
		sipResponseHeaders.setCallId(sipRequest.headers().getCallId());
		sipResponseHeaders.setCommandSequence(sipRequest.headers().getCommandSequence());
		sipResponseHeaders.setContentType(new SipMediaType("application", "sdp", Map.of()));
		var response = new SipResponse(
				new SipResponseLine(new SipVersion(2, 0), new SipStatusCode(486), "Busy here"),
				sipResponseHeaders,
				new byte[] {}
		);
		socketConnection.appendResponse(MESSAGE_SERIALIZER.serialize(response));
		socketConnection.changeOperation(OperationType.WRITE);
	}

	private static void sendInvite(SipRequest registrationRequest, SocketConnection socketConnection) {
		final byte[] sdpResponse = (
				"""
				v=0\r
				o=- 1234567890 1 IN IP4 0.0.0.0\r
				s=Talk\r
				c=IN IP4 0.0.0.0\r
				t=0 0\r
				m=audio 49237 RTP/AVP 96\r
				a=rtpmap:96 opus/48000/2\r
				a=fmtp:96 useinbandfec=1\r
				a=rtcp:40134\r
				\r"""

				).getBytes(StandardCharsets.UTF_8);

		var headers = new SipRequestHeaders();
		headers.addVia(CURRENT_VIA);
		headers.setMaxForwards(70);
		headers.setFrom(createFrom());
		// addParam("tag", UUID.nameUUIDFromBytes(registrationRequest.headers().getTo().sipURI().getURIAsString().getBytes()).toString())
		headers.setTo(new AddressOfRecord(
				registrationRequest.headers().getFrom().name(),
				registrationRequest.headers().getFrom().sipURI(),
				Map.of()
		));
		headers.setContactList(calculateContactSet(registrationRequest));
		headers.setCallId(String.valueOf(new Random().nextInt()));
		headers.setCommandSequence(new CommandSequence(1, "INVITE"));
		headers.setContentType(new SipMediaType("application", "sdp", Map.of()));
		headers.setContentLength(sdpResponse.length);

		var sipRequest = new SipRequest(
				new SipRequestLine("INVITE", registrationRequest.headers().getFrom().sipURI(), new SipVersion(2, 0)),
				headers,
				sdpResponse
		);
		socketConnection.appendResponse(MESSAGE_SERIALIZER.serialize(sipRequest));
		socketConnection.changeOperation(OperationType.WRITE);
	}

	private static AddressOfRecord createFrom() {
		return new AddressOfRecord(
				"George " + new Random().nextInt(),
				getCurrentSipURI(),
				Map.of("tag", UUID.nameUUIDFromBytes(getCurrentSipURI().getURIAsString().getBytes()).toString())
		);
	}

	private static void sendOK(SipRequest sipRequest, SocketConnection socketConnection) {
		var sipResponseHeaders = new SipResponseHeaders();
		for (Via via : sipRequest.headers().getViaList()) {
			sipResponseHeaders.addVia(via.normalize());
//					.addParameter("received", getIpAddress(socketConnection)));
		}
		sipResponseHeaders.addVia(CURRENT_VIA);
		sipResponseHeaders.setFrom(sipRequest.headers().getFrom());
		sipResponseHeaders.setTo(sipRequest.headers().getTo()
				.addParam("tag", UUID.nameUUIDFromBytes(sipRequest.headers().getTo().sipURI().getURIAsString().getBytes()).toString())
		);
		sipResponseHeaders.setContactList(calculateContactSet(sipRequest));
		sipResponseHeaders.setCallId(sipRequest.headers().getCallId());
		sipResponseHeaders.setCommandSequence(sipRequest.headers().getCommandSequence());
		sipResponseHeaders.setContentType(new SipMediaType("application", "sdp", Map.of()));
		final byte[] sdpResponse = (
				"""
				v=0\r
				o=- 1234567890 1 IN IP4 0.0.0.0\r
				s=Talk\r
				c=IN IP4 0.0.0.0\r
				t=0 0\r
				m=audio 49237 RTP/AVP 96\r
				a=rtpmap:96 opus/48000/2\r
				a=fmtp:96 useinbandfec=1\r
				a=rtcp:40134\r
				\r"""
		).getBytes(StandardCharsets.UTF_8);

		sipResponseHeaders.setContentLength(sdpResponse.length);
		var response = new SipResponse(
				new SipResponseLine(new SipVersion(2, 0), new SipStatusCode(200), "OK"),
				sipResponseHeaders,
				sdpResponse
		);
		socketConnection.appendResponse(MESSAGE_SERIALIZER.serialize(response));
		socketConnection.changeOperation(OperationType.WRITE);
	}

	private static String getIpAddress(SocketConnection socketConnection) {
		return socketConnection.getAddress().toString().substring(1);
	}

	private static int getPort() {
		return Optional.ofNullable(System.getenv("PORT"))
				.map(Integer::parseInt)
				.orElse(5068);
	}

	private static String getHost() {
		return Optional.ofNullable(System.getenv("HOST"))
				.orElse("0.0.0.0");
	}

}