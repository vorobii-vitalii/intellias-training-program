package sip;

import java.util.Map;

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
public record SipMediaType(String mediaType, String mediaSubType, Map<String, String> parameters) {
	private static final String SLASH = "/";
	private static final String SEMI_COLON = ";";

	public static SipMediaType parse(String str) {
		var arr = str.split(SEMI_COLON, 2);
		String[] mediaTypeComponents = arr[0].trim().split(SLASH);
		if (mediaTypeComponents.length != 2) {
			throw new IllegalArgumentException("Invalid media type:  m-type SLASH m-subtype *(SEMI m-parameter)");
		}
		var mediaType = mediaTypeComponents[0].trim();
		var mediaSubType = mediaTypeComponents[1].trim();
		return new SipMediaType(mediaType, mediaSubType, SipParseUtils.parseParameters(arr[1], SEMI_COLON));
	}

}
