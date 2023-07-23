package sip.request_handling.normalize;

import tcp.server.SocketConnection;

public record SipRequestNormalizeContext(SocketConnection socketConnection) {
}
