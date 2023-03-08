package http;

import util.Serializable;

public record HTTPResponse(HTTPResponseLine responseLine, HTTPHeaders httpHeaders) implements Serializable {

	@Override
	public byte[] serialize() {
		return new byte[0];
	}

}
