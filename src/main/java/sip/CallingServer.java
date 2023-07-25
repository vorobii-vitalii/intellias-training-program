package sip;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.spi.SelectorProvider;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

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
import sip.request_handling.BindingUpdateResponsePostProcessor;
import sip.request_handling.SDPMediaAddressProcessor;
import sip.request_handling.SDPReplacementSipResponsePostProcessor;
import sip.request_handling.SIPConnectionPreparer;
import sip.request_handling.SipMessageNetworkRequestHandler;
import sip.request_handling.SipResponseHandler;
import sip.request_handling.TCPConnectionsContext;
import sip.request_handling.ViaCreator;
import sip.request_handling.calls.InMemoryCallsRepository;
import sip.request_handling.enricher.CompositeUpdater;
import sip.request_handling.enricher.ContactListFixerSipRequestUpdater;
import sip.request_handling.enricher.ProxyViaSipRequestUpdater;
import sip.request_handling.media.InMemoryMediaMappingStorage;
import sip.request_handling.media.MediaCallInitiator;
import sip.request_handling.normalize.Normalizer;
import sip.request_handling.normalize.SipRequestViaParameterNormalizer;
import sip.request_handling.register.AckRequestHandler;
import sip.request_handling.register.ByeRequestProcessor;
import sip.request_handling.register.InMemoryBindingStorage;
import sip.request_handling.invite.InviteRequestHandler;
import sip.request_handling.register.RegisterSipMessageHandler;
import stun.StunMessage;
import stun.StunMessageReader;
import tcp.MessageSerializer;
import tcp.server.BufferCopier;
import tcp.server.ByteBufferPool;
import tcp.server.GenericServer;
import tcp.server.ServerConfig;
import tcp.server.SocketMessageReaderImpl;
import tcp.server.TCPServerConfigurer;
import tcp.server.UDPServerConfigurer;
import tcp.server.handler.GenericReadOperationHandler;
import tcp.server.handler.WriteOperationHandler;
import udp.ForwardingUDPReadOperationHandler;
import udp.RTPMessage;
import udp.RTPPacketTypeRecognizer;
import udp.UDPPacket;
import udp.UDPReadOperationHandler;
import udp.UDPWriteOperationHandler;
import util.Pair;

public class CallingServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(CallingServer.class);
	public static final int BUFFER_EXPIRATION_TIME_MILLIS = 10_000;
	public static final int REQUEST_QUEUE_CAPACITY = 10_000;
	public static final Via CURRENT_VIA = new Via(new SipSentProtocol("SIP", "2.0", "TCP"),
			new Address(getHost(), getSipServerPort()),
			Map.of("branch", "z9hG4bK25235636")
	);
	public static final SimpleMeterRegistry METER_REGISTRY = new SimpleMeterRegistry();
	public static final InMemoryMediaMappingStorage MEDIA_MAPPING_STORAGE = new InMemoryMediaMappingStorage();

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
//			LOGGER.info("UDP RTCP Packet: {}", request);
		};

		RequestHandler<UDPPacket<StunMessage>> stunMessageHandler = request -> {
//			LOGGER.info("UDP Stun Packet: {}", request);
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
				),
				new UDPServerConfigurer());
		server.start();
	}

	//
	private static void startRTPServer() {
		Map<Address, Deque<ByteBuffer>> map = new ConcurrentHashMap<>();

		var rtpMessagesQueue = new ArrayBlockingQueue<UDPPacket<RTPMessage>>(REQUEST_QUEUE_CAPACITY);

		RequestHandler<UDPPacket<RTPMessage>> rtpMessageHandler = request -> {
//			LOGGER.info("UDP RTP Packet: {}", request);
		};

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
						OP_READ, new ForwardingUDPReadOperationHandler(
								2_000,
								MEDIA_MAPPING_STORAGE,
								new BufferCopier(BUFFER_POOL),
								List.of(new RTPPacketTypeRecognizer()),
								map
						),
						OP_WRITE, new UDPWriteOperationHandler(
								map
						)
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
		var bindingStorage = new InMemoryBindingStorage();
		var callsRepository = new InMemoryCallsRepository();
		List<SDPMediaAddressProcessor> sdpMediaAddressProcessors = List.of(new RTPMediaAddressProcessor(
				new Address(getHost(), getRTPServerPort())
		));
		var mediaCallInitiator = new MediaCallInitiator(MEDIA_MAPPING_STORAGE);
		final CompositeUpdater<SipRequest> sipRequestUpdater = new CompositeUpdater<>(
				List.of(
						new ContactListFixerSipRequestUpdater(
								() -> new ContactSet(Set.of(new AddressOfRecord("", getCurrentSipURI(), Map.of())))),
						new ProxyViaSipRequestUpdater(CURRENT_VIA))
		);
		RequestHandler<NetworkRequest<SipMessage>> sipRequestHandler = new SipMessageNetworkRequestHandler(
				List.of(
						new RegisterSipMessageHandler(MESSAGE_SERIALIZER, bindingStorage),
						new InviteRequestHandler(
								bindingStorage,
								MESSAGE_SERIALIZER,
								sdpMediaAddressProcessors,
								callsRepository,
								sipRequestUpdater
						),
						new AckRequestHandler(
								bindingStorage,
								MESSAGE_SERIALIZER,
								callsRepository,
								mediaCallInitiator,
								sipRequestUpdater
						),
						new ByeRequestProcessor(
								callsRepository,
								bindingStorage,
								MESSAGE_SERIALIZER,
								sipRequestUpdater
						)
				),
				new SipResponseHandler(
						List.of(
								new BindingUpdateResponsePostProcessor(callsRepository),
								new SDPReplacementSipResponsePostProcessor(
										callsRepository,
										sdpMediaAddressProcessors
								),
								new ProxyAttributesAppenderSipResponsePostProcessor(
										CURRENT_VIA,
										sdpMediaAddressProcessors, new AddressOfRecord("", getCurrentSipURI(), Map.of()))
						),
						MESSAGE_SERIALIZER,
						callsRepository
				),
				new Normalizer<>(List.of(new SipRequestViaParameterNormalizer())));
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

	private static FullSipURI getCurrentSipURI() {
		return new FullSipURI(
				"sip",
				new Credentials(null, null),
				new Address(getHost(), getSipServerPort()),
				Map.of("transport", "tcp"),
				Map.of()
		);
	}
	// SSL Engine, test using SSL engine
	// max udp = 1536 - headers size

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

}