package sip;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import util.Serializable;

//media-type       =  m-type SLASH m-subtype *(SEMI m-parameter)
//m-type           =  discrete-type / composite-type
//discrete-type    =  "text" / "image" / "audio" / "video"
/// "application" / extension-token
//composite-type   =  "message" / "multipart" / extension-token
//extension-token  =  ietf-token / x-token
//ietf-token       =  token
//x-token          =  "x-" token
//m-subtype        =  extension-token / iana-token
//iana-token       =  token
//m-parameter      =  m-attribute EQUAL m-value
//m-attribute      =  token
//m-value          =  token / quoted-string
public record SipMediaType(String mediaType, String mediaSubType, Map<String, String> parameters) implements Serializable {
	private static final String SLASH = "/";
	private static final String SEMI_COLON = ";";
	public static final int PARAMETER_LIST_DELIMITER_LENGTH = 1;
	public static final int PARAMETER_DELIMITER_LENGTH = 1;
	public static final byte PARAMETERS_DELIMITER_CHAR = (byte) ';';
	public static final char PARAMETER_DELIMITER = '=';
	public static final char SLASH_CHAR = '/';

	public static SipMediaType parse(String str) {
		var arr = str.split(SEMI_COLON, 2);
		String[] mediaTypeComponents = arr[0].trim().split(SLASH);
		if (mediaTypeComponents.length != 2) {
			throw new IllegalArgumentException("Invalid media type:  m-type SLASH m-subtype *(SEMI m-parameter)");
		}
		var mediaType = mediaTypeComponents[0].trim();
		var mediaSubType = mediaTypeComponents[1].trim();
		Map<String, String> parameters = arr.length > 1 ? SipParseUtils.parseParameters(arr[1], SEMI_COLON) : Map.of();
		return new SipMediaType(mediaType, mediaSubType, parameters);
	}

	@Override
	public void serialize(ByteBuffer dest) {
		dest.put(mediaType.getBytes(StandardCharsets.UTF_8));
		dest.put((byte) SLASH_CHAR);
		dest.put(mediaSubType.getBytes(StandardCharsets.UTF_8));
		for (var entry : parameters.entrySet()) {
			dest.put(PARAMETERS_DELIMITER_CHAR);
			dest.put(entry.getKey().getBytes(StandardCharsets.UTF_8));
			dest.put((byte) PARAMETER_DELIMITER);
			dest.put(entry.getValue().getBytes(StandardCharsets.UTF_8));
		}
	}

	@Override
	public int getSize() {
		return mediaType.length() + 1 + mediaSubType.length() + getParametersInBytes();
	}

	private int getParametersInBytes() {
		int total = 0;
		for (var entry : parameters.entrySet()) {
			total += PARAMETER_LIST_DELIMITER_LENGTH;
			total += entry.getKey().length();
			total += PARAMETER_DELIMITER_LENGTH;
			total += entry.getValue().length();
		}
		return total;
	}

}
