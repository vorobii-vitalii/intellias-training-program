package http;

import util.Serializable;

public record HTTPResponse(HTTPResponseLine responseLine, HTTPHeaders httpHeaders, byte[] body)
				implements Serializable {

	public static final int CODE_SWITCHING_PROTOCOL = 101;

	public boolean isUpgradeResponse() {
		return responseLine.statusCode() == CODE_SWITCHING_PROTOCOL;
	}

	public String getUpgradeProtocol() {
		return httpHeaders.getHeaderValues("Upgrade").get(0);
	}

	@Override
	public byte[] serialize() {
		return merge(responseLine.serialize(), httpHeaders.serialize(), body);
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