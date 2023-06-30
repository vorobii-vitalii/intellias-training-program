package sip;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.spi.SelectorProvider;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import message_passing.BlockingQueueMessageProducer;
import request_handler.NetworkRequest;
import request_handler.RequestHandler;
import request_handler.RequestProcessor;
import tcp.server.ByteBufferPool;
import tcp.server.SocketMessageReaderImpl;
import tcp.server.TCPServer;
import tcp.server.TCPServerConfig;
import tcp.server.handler.GenericReadOperationHandler;
import tcp.server.handler.WriteOperationHandler;

public class SipServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(SipServer.class);
	public static final int BUFFER_EXPIRATION_TIME_MILLIS = 10_000;
	public static final int REQUEST_QUEUE_CAPACITY = 10_000;

	private static void startProcess(Runnable process, String processName) {
		var thread = new Thread(process);
		thread.setName(processName);
		thread.setUncaughtExceptionHandler((t, e) -> LOGGER.error("Error in thread {}", t, e));
		thread.start();
	}

	public static void main(String[] args) {
		var bufferPool = new ByteBufferPool(ByteBuffer::allocate, BUFFER_EXPIRATION_TIME_MILLIS);

		var requestQueue = new ArrayBlockingQueue<NetworkRequest<SipRequest>>(REQUEST_QUEUE_CAPACITY);

		var meterRegistry = new SimpleMeterRegistry();
		var sipRequestHandleTimer = Timer.builder("sip.request.time").register(meterRegistry);
		var sipRequestCount = Counter.builder("sip.request.count").register(meterRegistry);
		RequestHandler<NetworkRequest<SipRequest>> sipRequestHandler = request -> {
			LOGGER.info("Received request {} from {}", request.request(), request.socketConnection());
		};
		RequestProcessor<NetworkRequest<SipRequest>> requestProcessor =
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
						OP_ACCEPT, new SipAcceptOperationHandler(bufferPool),
						OP_READ, sipReadHandler
//						OP_WRITE, new WriteOperationHandler()
				));
		server.start();
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
