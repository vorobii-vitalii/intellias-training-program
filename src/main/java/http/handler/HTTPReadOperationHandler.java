package http.handler;

import http.HTTPRequest;
import http.HTTPResponse;
import http.reader.HTTPRequestMessageReader;
import tcp.server.SocketMessageReader;
import request_handler.ProcessingRequest;
import tcp.server.ServerAttachment;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HTTPReadOperationHandler implements Consumer<SelectionKey> {
	private final Map<String, ProtocolChanger> protocolChangerMap;
	private final SocketMessageReader<HTTPRequest> socketMessageReader =
					new SocketMessageReader<>(new HTTPRequestMessageReader((name, val) -> Collections.singletonList(val.trim())));
	private final BlockingQueue<ProcessingRequest<HTTPRequest, HTTPResponse>> requestQueue;

	public HTTPReadOperationHandler(
					BlockingQueue<ProcessingRequest<HTTPRequest, HTTPResponse>> requestQueue,
					Collection<ProtocolChanger> protocolChangers
	) {
		this.protocolChangerMap = protocolChangers.stream()
						.collect(Collectors.toMap(ProtocolChanger::getProtocolName, Function.identity()));
		this.requestQueue = requestQueue;
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		var serverAttachment = ((ServerAttachment) selectionKey.attachment());
		try {
			var socketChannel = (SocketChannel) selectionKey.channel();
			var request = socketMessageReader.readMessage(serverAttachment.bufferContext(), socketChannel);
			if (request != null) {
				requestQueue.add(new ProcessingRequest<>(request) {
					@Override
					public void onResponse(HTTPResponse responseMessage) {
						onMessageResponse(request, responseMessage, selectionKey);
					}
				});
			}
		}
		catch (Exception e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}

	private void onMessageResponse(HTTPRequest request, HTTPResponse response, SelectionKey selectionKey) {
		System.out.println("Response: " + response);
		if (response.isUpgradeResponse()) {
			var protocolChanger = protocolChangerMap.get(response.getUpgradeProtocol());
			if (protocolChanger == null) {
				throw new IllegalArgumentException("Not supported protocol " + response.getUpgradeProtocol());
			}
			protocolChanger.changeProtocol(new ProtocolChangeContext(request, response, selectionKey));
		}
		var attachmentObject = (ServerAttachment) (selectionKey.attachment());
		attachmentObject.responses().add(response);
		selectionKey.interestOps(SelectionKey.OP_WRITE);
		selectionKey.selector().wakeup();
	}
}
