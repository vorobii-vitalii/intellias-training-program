package request_handler;

import tcp.server.SocketConnection;

import java.nio.channels.SelectionKey;

public record NetworkRequest<Request>(Request request, SocketConnection socketConnection) {
}
