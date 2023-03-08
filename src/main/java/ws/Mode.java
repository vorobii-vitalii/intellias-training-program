package ws;

import http.HTTPRequest;

public final class Mode<RequestObject> {
	public static final Mode<HTTPRequest> HANDSHAKE = new Mode<>();

	private Mode() {
		// Constant classes should not be instantiated
	}
}
