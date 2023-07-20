package sip;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

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
import rtcp.RTCPMessage;
import rtcp.RTCPMessagesReader;
import sip.request_handling.ProxyAttributesAppenderSipResponsePostProcessor;
import sip.request_handling.RTPMediaAddressProcessor;
import sip.request_handling.RemoveRejectedCallResponsePostProcessor;
import sip.request_handling.SDPMediaAddressProcessor;
import sip.request_handling.SDPReplacementSipResponsePostProcessor;
import sip.request_handling.SIPConnectionPreparer;
import sip.request_handling.SipRequestMessageHandler;
import sip.request_handling.SipResponseHandler;
import sip.request_handling.TCPConnectionsContext;
import sip.request_handling.calls.InMemoryCallsRepository;
import sip.request_handling.register.InMemoryBindingStorage;
import sip.request_handling.register.InviteRequestHandler;
import sip.request_handling.register.RegisterSipMessageHandler;
import stun.StunMessage;
import stun.StunMessageReader;
import tcp.MessageSerializer;
import tcp.server.ByteBufferPool;
import tcp.server.OperationType;
import tcp.server.SocketConnection;
import tcp.server.SocketMessageReaderImpl;
import tcp.server.GenericServer;
import tcp.server.ServerConfig;
import tcp.server.TCPServerConfigurer;
import tcp.server.UDPServerConfigurer;
import tcp.server.UnsafeSupplier;
import tcp.server.handler.GenericReadOperationHandler;
import tcp.server.handler.WriteOperationHandler;
import udp.RTPMessage;
import udp.RTPMessageReader;
import udp.UDPPacket;
import udp.UDPReadOperationHandler;
import util.Pair;

public class CallingServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(CallingServer.class);
	public static final int BUFFER_EXPIRATION_TIME_MILLIS = 10_000;
	public static final int REQUEST_QUEUE_CAPACITY = 10_000;
	public static final Via CURRENT_VIA = new Via(new SipSentProtocol("SIP", "2.0", "TCP"),
			new Address(getHost(), getSipServerPort()),
			Map.of("branch", "z9hG4bK25235636", "received", "127.0.0.1")
	);
	public static final SimpleMeterRegistry METER_REGISTRY = new SimpleMeterRegistry();

	private static void startProcess(Runnable process, String processName) {
		var thread = new Thread(process);
		thread.setName(processName);
		thread.setUncaughtExceptionHandler((t, e) -> LOGGER.error("Error in thread {}", t, e));
		thread.start();
	}

	private static final ByteBufferPool BUFFER_POOL = new ByteBufferPool(ByteBuffer::allocate, BUFFER_EXPIRATION_TIME_MILLIS);
	private static final MessageSerializer MESSAGE_SERIALIZER = new MessageSerializer(BUFFER_POOL);

	public static void main(String[] args) throws IOException {
		startRTPServer();
		startRTCPServer();
		startSipServer();
	}

	private static void startRTCPServer() {
		var rtpMessagesQueue = new ArrayBlockingQueue<UDPPacket<List<RTCPMessage>>>(REQUEST_QUEUE_CAPACITY);
		var stunMessageQueue = new ArrayBlockingQueue<UDPPacket<StunMessage>>(REQUEST_QUEUE_CAPACITY);

		RequestHandler<UDPPacket<List<RTCPMessage>>> rtpMessageHandler = request -> {
			LOGGER.info("UDP RTCP Packet: {}", request);
		};

		RequestHandler<UDPPacket<StunMessage>> stunMessageHandler = request -> {
			LOGGER.info("UDP Stun Packet: {}", request);
		};

		var rtcpRequestHandleTimer = Timer.builder("rtcp.request.time").register(METER_REGISTRY);
		var rtcpRequestsCount = Counter.builder("rtcp.request.count").register(METER_REGISTRY);


		RequestProcessor<UDPPacket<List<RTCPMessage>>> requestProcessor =
				new RequestProcessor<>(rtpMessagesQueue, rtpMessageHandler, rtcpRequestHandleTimer, rtcpRequestsCount);

		RequestProcessor<UDPPacket<StunMessage>> stunRequestProcessor =
				new RequestProcessor<>(stunMessageQueue, stunMessageHandler, rtcpRequestHandleTimer, rtcpRequestsCount);

		startProcess(requestProcessor, "RTCP request processor");
		startProcess(stunRequestProcessor, "STUN request processor");

		var server = new GenericServer(
				ServerConfig.builder()
						.setName("RTPC server")
						.setHost(getHost())
						.setPort(getRTCPServerPort())
						.setProtocolFamily(StandardProtocolFamily.INET)
						.setInterestOps(OP_READ)
						.onConnectionClose(connection -> {
							LOGGER.info("Connection closed");
						})
						.build(),
				SelectorProvider.provider(),
				System.err::println,
				Map.of(
						OP_READ, new UDPReadOperationHandler(
								2_000,
								List.of(
										new Pair<>(
												new StunMessageReader(),
												new BlockingQueueMessageProducer<>(stunMessageQueue)
										),
										new Pair<>(
												new RTCPMessagesReader(List.of()),
												new BlockingQueueMessageProducer<>(rtpMessagesQueue)
										)
								)
						)
						//						OP_WRITE, new WriteOperationHandler(
						//								sipWriteTimer,
						//								sipWriteCount,
						//								(key, e) -> {
						//									LOGGER.warn("Error", e);
						//								},
						//								BUFFER_POOL,
						//								OpenTelemetry.noop()
						//						)
				),
				new UDPServerConfigurer());
		server.start();
	}

	private static void startRTPServer() {
		var rtpMessagesQueue = new ArrayBlockingQueue<UDPPacket<RTPMessage>>(REQUEST_QUEUE_CAPACITY);

		RequestHandler<UDPPacket<RTPMessage>> rtpMessageHandler = request -> {
			LOGGER.info("UDP RTP Packet: {}", request);
		};

		var rtpRequestHandleTimer = Timer.builder("rtp.request.time").register(METER_REGISTRY);
		var rtpRequestsCount = Counter.builder("rtp.request.count").register(METER_REGISTRY);


		RequestProcessor<UDPPacket<RTPMessage>> requestProcessor =
				new RequestProcessor<>(rtpMessagesQueue, rtpMessageHandler, rtpRequestHandleTimer, rtpRequestsCount);

		startProcess(requestProcessor, "RTP request processor");

		var server = new GenericServer(
				ServerConfig.builder()
						.setName("RTP server")
						.setHost(getHost())
						.setPort(getRTPServerPort())
						.setProtocolFamily(StandardProtocolFamily.INET)
						.setInterestOps(OP_READ)
						.onConnectionClose(connection -> {
							LOGGER.info("Connection closed");
						})
						.build(),
				SelectorProvider.provider(),
				System.err::println,
				Map.of(
						OP_READ, new UDPReadOperationHandler(
								2_000,
								List.of(
										new Pair<>(
												new RTPMessageReader(),
												new BlockingQueueMessageProducer<>(rtpMessagesQueue)
										)
								)
						)
//						OP_WRITE, new WriteOperationHandler(
//								sipWriteTimer,
//								sipWriteCount,
//								(key, e) -> {
//									LOGGER.warn("Error", e);
//								},
//								BUFFER_POOL,
//								OpenTelemetry.noop()
//						)
				),
				new UDPServerConfigurer());
		server.start();
	}

	private static void startSipServer() throws IOException {
		var requestQueue = new ArrayBlockingQueue<NetworkRequest<SipMessage>>(REQUEST_QUEUE_CAPACITY);

		var sipRequestHandleTimer = Timer.builder("sip.request.time").register(METER_REGISTRY);
		var sipWriteTimer = Timer.builder("sip.response.write.time").register(METER_REGISTRY);
		var sipRequestCount = Counter.builder("sip.request.count").register(METER_REGISTRY);
		var sipWriteCount = Counter.builder("sip.response.write.count").register(METER_REGISTRY);

		var selectorProvider = SelectorProvider.provider();
		var selector = selectorProvider.openSelector();

		var tcpConnectionsContext = new TCPConnectionsContext(new SIPConnectionPreparer(BUFFER_POOL, selector));

//		RequestHandler<NetworkRequest<SipMessage>> sipRequestHandler = request -> {
//			var sipMessage = request.request();
//			LOGGER.info("Received SIP message {} from {}", sipMessage, request.socketConnection());
//			if (sipMessage instanceof SipRequest sipRequest) {
//				switch (sipRequest.requestLine().method()) {
//					case "ACK" -> {
//						sendOK(sipRequest, request.socketConnection());
//					}
//					case "REGISTER" -> {
//						LOGGER.info("Client registered");
//						EXECUTOR_SERVICE.schedule(() -> {
//							sendInvite(sipRequest, request.socketConnection());
//						}, 5000, TimeUnit.MILLISECONDS);
//						//					SIP/2.0 200 OK
//						//					Via: SIP/2.0/UDP bobspc.biloxi.com:5060;branch=z9hG4bKnashds7
//						//					;received=192.0.2.4
//						//					To: Bob <sip:bob@biloxi.com>;tag=2493k59kd
//						//					From: Bob <sip:bob@biloxi.com>;tag=456248
//						//					Call-ID: 843817637684230@998sdasdh09
//						//					CSeq: 1826 REGISTER
//						//					Contact: <sip:bob@192.0.2.4>
//						//							Expires: 7200
//						//					Content-Length: 0
//						sendOK(sipRequest, request.socketConnection());
//					}
//					case "INVITE" -> {
//						LOGGER.info("Client calling");
//						var sipResponseHeaders = new SipResponseHeaders();
//						for (Via via : sipRequest.headers().getViaList()) {
//							sipResponseHeaders.addVia(via.normalize());
//						}
//						sipResponseHeaders.addVia(CURRENT_VIA);
//						sipResponseHeaders.setFrom(sipRequest.headers().getFrom());
//						sipResponseHeaders.setTo(sipRequest.headers().getTo()
//								.addParam("tag", UUID.nameUUIDFromBytes(sipRequest.headers().getTo().sipURI().getURIAsString().getBytes()).toString())
//						);
//						sipResponseHeaders.setContactList(calculateContactSet(sipRequest));
//						sipResponseHeaders.setCallId(sipRequest.headers().getCallId());
//						sipResponseHeaders.setCommandSequence(sipRequest.headers().getCommandSequence());
//						var response = new SipResponse(
//								new SipResponseLine(new SipVersion(2, 0), new SipStatusCode(180), "Ringing"),
//								sipResponseHeaders,
//								new byte[] {}
//						);
//						request.socketConnection().appendResponse(MESSAGE_SERIALIZER.serialize(response));
//						request.socketConnection().changeOperation(OperationType.WRITE);
//						EXECUTOR_SERVICE.schedule(() -> {
//							if (counter.getAndIncrement() % 2 == 0) {
//								sendOK(sipRequest, request.socketConnection());
//							} else {
//								sendBusy(sipRequest, request.socketConnection());
//							}
//						}, 3000, TimeUnit.MILLISECONDS);
//					}
//				}
//			}
//
//		};

		var bindingStorage = new InMemoryBindingStorage();
		var callsRepository = new InMemoryCallsRepository();
		List<SDPMediaAddressProcessor> sdpMediaAddressProcessors = List.of(new RTPMediaAddressProcessor(
				new Address(getHost(), getRTPServerPort())
		));
		RequestHandler<NetworkRequest<SipMessage>> sipRequestHandler = new SipRequestMessageHandler(
				List.of(
						new RegisterSipMessageHandler(
								MESSAGE_SERIALIZER,
								bindingStorage,
								CURRENT_VIA
						),
						new InviteRequestHandler(
								bindingStorage,
								MESSAGE_SERIALIZER,
								CURRENT_VIA,
								getCurrentSipURI(),
								sdpMediaAddressProcessors,
								callsRepository
						)
				),
				new SipResponseHandler(
						List.of(
								new RemoveRejectedCallResponsePostProcessor(callsRepository),
								new SDPReplacementSipResponsePostProcessor(
										callsRepository,
										sdpMediaAddressProcessors
								),
								new ProxyAttributesAppenderSipResponsePostProcessor(
										CURRENT_VIA,
										getCurrentSipURI()
								)
						),
						MESSAGE_SERIALIZER,
						callsRepository));
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

		var server = new GenericServer(
				ServerConfig.builder()
						.setHost(getHost())
						.setPort(getSipServerPort())
						.setProtocolFamily(StandardProtocolFamily.INET)
						.onConnectionClose(connection -> {
							LOGGER.info("Connection closed");
						})
						.build(),
				selectorProvider,
				System.err::println,
				Map.of(
						OP_ACCEPT, new SipAcceptOperationHandler(tcpConnectionsContext),
						OP_READ, sipReadHandler,
						OP_WRITE, new WriteOperationHandler(
								sipWriteTimer,
								sipWriteCount,
								(key, e) -> {
//									LOGGER.warn("Error", e);
								},
								BUFFER_POOL,
								OpenTelemetry.noop()
						)
				),
				new TCPServerConfigurer(),
				() -> selector);
		server.start();
	}

	// Received request SipRequest{requestLine=SipRequestLine[method=REGISTER, requestURI=FullSipURI[protocol=sip, credentials=Credentials[username=service, password=null], address=Address[host=0.0.0.0, port=5068], uriParameters={}, queryParameters={}], version=SipVersion[majorVersion=2, minorVersion=0]], headers=SipHeaders{headerMap={subject=[Performance Test]}, viaList=[Via[sipSentProtocol=SipSentProtocol[protocolName=SIP, protocolVersion=2.0, transportName=TCP], sentBy=Address[host=127.0.0.1, port=36297], parameters={branch=z9hG4bK-59879-62-0}]], from=AddressOfRecord[name=sipp, sipURI=FullSipURI[protocol=sip, credentials=Credentials[username=sipp, password=null], address=Address[host=127.0.0.1, port=36297], uriParameters={}, queryParameters={}], parameters={tag=59879SIPpTag0062}], to=AddressOfRecord[name=service, sipURI=FullSipURI[protocol=sip, credentials=Credentials[username=service, password=null], address=Address[host=0.0.0.0, port=5068], uriParameters={}, queryParameters={}], parameters={}], referTo=null, commandSequence=CommandSequence[sequenceNumber=1, commandName=REGISTER], maxForwards=70, contentLength=0, contactList=ContactSet[allowedAddressOfRecords=[AddressOfRecord[name=Anonymous, sipURI=FullSipURI[protocol=sip, credentials=Credentials[username=sipp, password=null], address=Address[host=127.0.0.1, port=36297], uriParameters={}, queryParameters={}], parameters={}]]], contentType=SipMediaType[mediaType=application, mediaSubType=sdp, parameters={}], callId=62-59879@127.0.0.1, expires=null}, payload=[]}

	private static ContactSet calculateContactSet(SipRequest sipRequest) {
		final AddressOfRecord to = sipRequest.headers().getTo();
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
				new Address(getHost(), getSipServerPort()),
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
		final byte[] sdpResponse = getSdpResponse();

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
		final byte[] sdpResponse = getSdpResponse();

		sipResponseHeaders.setContentLength(sdpResponse.length);
		var response = new SipResponse(
				new SipResponseLine(new SipVersion(2, 0), new SipStatusCode(200), "OK"),
				sipResponseHeaders,
				sdpResponse
		);
		socketConnection.appendResponse(MESSAGE_SERIALIZER.serialize(response));
		socketConnection.changeOperation(OperationType.WRITE);
	}

	// SSL Engine, test using SSL engine
	// max udp = 1536 - headers size


	private static byte[] getSdpResponse() {
		return (
				"""
						v=0\r
						o=John 3660 1765 IN IP4 127.0.0.1\r
						s=Talk\r
						c=IN IP4 127.0.0.1\r
						t=0 0\r
						a=rtcp-xr:rcvr-rtt=all:10000 stat-summary=loss,dup,jitt,TTL voip-metrics\r
						a=record:off\r
						m=audio %s RTP/AVP 96 97 98 0 8 3 9 99 18 100 102 10 11 101 103 104 105 106\r
						a=rtpmap:96 opus/48000/2\r
						a=fmtp:96 useinbandfec=1\r
						a=rtpmap:97 speex/16000\r
						a=fmtp:97 vbr=on\r
						a=rtpmap:98 speex/8000\r
						a=fmtp:98 vbr=on\r
						a=rtpmap:99 iLBC/8000\r
						a=fmtp:99 mode=30\r
						a=fmtp:18 annexb=yes\r
						a=rtpmap:100 speex/32000\r
						a=fmtp:100 vbr=on\r
						a=rtpmap:102 BV16/8000\r
						a=rtpmap:101 telephone-event/48000\r
						a=rtpmap:103 telephone-event/16000\r
						a=rtpmap:104 telephone-event/8000\r
						a=rtpmap:105 telephone-event/32000\r
						a=rtpmap:106 telephone-event/44100\r
						a=rtcp:%s\r
						a=rtcp-fb:* trr-int 1000\r
						a=rtcp-fb:* ccm tmmbr\r
						\r"""

//				"""
//						v=0\r
//						o=- 1234567890 1 IN IP4 127.0.0.1\r
//						s=Talk\r
//						c=IN IP4 127.0.0.1\r
//						t=0 0\r
//						m=audio %s RTP/AVP 96\r
//						a=rtpmap:96 opus/48000/2\r
//						a=fmtp:96 useinbandfec=1\r
//						a=rtcp:40134\r
//						\r"""
				.formatted(getRTPServerPort(), getRTCPServerPort())
		).getBytes(StandardCharsets.UTF_8);
	}

	private static String getIpAddress(SocketConnection socketConnection) {
		return socketConnection.getAddress().toString().substring(1);
	}

	private static int getSipServerPort() {
		return Optional.ofNullable(System.getenv("SIP_PORT"))
				.map(Integer::parseInt)
				.orElse(5068);
	}

	private static int getRTCPServerPort() {
		return Optional.ofNullable(System.getenv("RTCP_PORT"))
				.map(Integer::parseInt)
				.orElse(54257);
	}

	private static int getRTPServerPort() {
		return Optional.ofNullable(System.getenv("RTP_PORT"))
				.map(Integer::parseInt)
				.orElse(54258);
	}

	private static String getHost() {
		return Optional.ofNullable(System.getenv("HOST"))
				.orElse("127.0.0.1");
	}

	private static InetAddress getSocketAddress() {
		return new InetSocketAddress(getHost(), getSipServerPort()).getAddress();
	}

}