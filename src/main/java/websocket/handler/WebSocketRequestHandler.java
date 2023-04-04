package websocket.handler;

import exception.ParseException;
import tcp.server.ServerAttachment;
import tcp.server.SocketMessageReader;
import util.Constants;
import websocket.WebSocketMessage;
import websocket.endpoint.WebSocketEndpointProvider;
import websocket.reader.WebSocketMessageReader;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

public class WebSocketRequestHandler implements Consumer<SelectionKey> {
	private final SocketMessageReader<WebSocketMessage> socketMessageReader =
					new SocketMessageReader<>(new WebSocketMessageReader());
	private final WebSocketEndpointProvider webSocketEndpointProvider;

	public WebSocketRequestHandler(WebSocketEndpointProvider webSocketEndpointProvider) {
		this.webSocketEndpointProvider = webSocketEndpointProvider;
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		var serverAttachment = ((ServerAttachment) selectionKey.attachment());
		try {
			var socketChannel = (SocketChannel) selectionKey.channel();
			var webSocketMessage = socketMessageReader.readMessage(serverAttachment.readBufferContext(), socketChannel);
			if (webSocketMessage != null) {
				onMessageRead(webSocketMessage, selectionKey);
			}
		}
		catch (Exception e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}

	private void onMessageRead(WebSocketMessage message, SelectionKey selectionKey) {
		var attachmentObject = (ServerAttachment) (selectionKey.attachment());
		var context = attachmentObject.context();
		// TODO: Do it in another thread!
		var endpoint =
						webSocketEndpointProvider.getEndpoint(context.get(Constants.WebSocketMetadata.ENDPOINT).toString());
		endpoint.onMessage(selectionKey, message);
	}

}
