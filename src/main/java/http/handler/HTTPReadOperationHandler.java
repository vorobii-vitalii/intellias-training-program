package http.handler;

import http.HTTPRequest;
import http.HTTPResponse;
import http.reader.HTTPRequestLineMessageReader;
import tcp.server.handler.GenericRequestResponseReadOperationHandler;
import util.Constants;
import request_handler.ProcessingRequest;
import tcp.server.ServerAttachmentObject;
import writer.MessageWriter;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HTTPReadOperationHandler extends GenericRequestResponseReadOperationHandler<HTTPRequest, HTTPResponse> {
	private final Map<String, ProtocolChanger> protocolChangerMap;

	public HTTPReadOperationHandler(
					BlockingQueue<ProcessingRequest<HTTPRequest, HTTPResponse>> requestQueue,
					Collection<ProtocolChanger> protocolChangers
	) {
		super(requestQueue);
		this.protocolChangerMap = protocolChangers.stream()
						.collect(Collectors.toMap(ProtocolChanger::getProtocolName, Function.identity()));
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onMessageResponse(HTTPResponse httpResponse, SelectionKey selectionKey) {
		var attachmentObject = (ServerAttachmentObject<HTTPRequest>) (selectionKey.attachment());
		System.out.println("Response: " + httpResponse);
		if (!httpResponse.isUpgradeResponse()) {
			selectionKey.attach(new ServerAttachmentObject<>(
							Constants.Protocol.HTTP,
							attachmentObject.readBuffer(),
							new HTTPRequestLineMessageReader(new HTTPRequest()),
							new MessageWriter(ByteBuffer.wrap(httpResponse.serialize()), () -> {
								System.out.println("Message was written");
								selectionKey.interestOps(SelectionKey.OP_READ);
							})
			));
		} else {
			ProtocolChanger protocolChanger = protocolChangerMap.get(httpResponse.getUpgradeProtocol());
			if (protocolChanger == null) {
				throw new IllegalArgumentException("Not supported protocol " + httpResponse.getUpgradeProtocol());
			}
			selectionKey.attach(new ServerAttachmentObject<>(
							Constants.Protocol.HTTP,
							attachmentObject.readBuffer(),
							null,
							new MessageWriter(ByteBuffer.wrap(httpResponse.serialize()), () -> {
								System.out.println("Message was written");
								protocolChanger.changeProtocol(selectionKey);
							})
			));
		}
	}
}
