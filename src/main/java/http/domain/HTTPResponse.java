package http.domain;

import java.nio.ByteBuffer;

import util.Constants;
import util.Serializable;

public record HTTPResponse(HTTPResponseLine responseLine, HTTPHeaders httpHeaders, byte[] body)
				implements Serializable {

	public boolean isUpgradeResponse() {
		return responseLine.statusCode() == Constants.HTTPStatusCode.SWITCHING_PROTOCOL;
	}

	public String getUpgradeProtocol() {
		return httpHeaders.getHeaderValue(Constants.HTTPHeaders.UPGRADE).orElse(null);
	}

	@Override
	public byte[] serialize() {
		return merge(responseLine.serialize(), httpHeaders.serialize(), body);
	}

	@Override
	public void serialize(ByteBuffer dest) {
		responseLine.serialize(dest);
		httpHeaders.serialize(dest);
		dest.put(body);
	}

	@Override
	public int getSize() {
		return responseLine.getSize() + httpHeaders.getSize() + body.length;
	}

	private byte[] merge(byte[]... byteArrays) {
		var totalSize = 0;
		for (var byteArray : byteArrays) {
			totalSize += byteArray.length;
		}
		var mergedArr = new byte[totalSize];
		var current = 0;
		for (var byteArray : byteArrays) {
			System.arraycopy(byteArray, 0, mergedArr, current, byteArray.length);
			current += byteArray.length;
		}
		return mergedArr;
	}

}
