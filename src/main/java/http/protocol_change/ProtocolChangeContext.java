package http.protocol_change;

import http.domain.HTTPRequest;
import http.domain.HTTPResponse;
import tcp.server.SocketConnection;

public record ProtocolChangeContext(HTTPRequest request, HTTPResponse response, SocketConnection connection) {

}
