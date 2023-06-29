package sip;

//Via               =  ( "Via" / "v" ) HCOLON via-parm *(COMMA via-parm)
//via-parm          =  sent-protocol LWS sent-by *( SEMI via-params )
//via-params        =  via-ttl / via-maddr
/// via-received / via-branch
/// via-extension
//via-ttl           =  "ttl" EQUAL ttl
//via-maddr         =  "maddr" EQUAL host
//via-received      =  "received" EQUAL (IPv4address / IPv6address)
//via-branch        =  "branch" EQUAL token
//via-extension     =  generic-param
//sent-protocol     =  protocol-name SLASH protocol-version SLASH transport
//protocol-name     =  "SIP" / token
//protocol-version  =  token
//transport         =  "UDP" / "TCP" / "TLS" / "SCTP"
/// other-transport
//sent-by           =  host [ COLON port ]
//ttl               =  1*3DIGIT ; 0 to 255
// Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bKnashds8

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record Via(SipSentProtocol sipSentProtocol, Address sentBy, Map<String, String> parameters) {
	private static final int NOT_FOUND = -1;
	public static final String PARAMETERS_DELIMITER = ";";

	public static List<Via> parseMultiple(String str) {
		var viaAddresses = str.split(",");
		return Arrays.stream(viaAddresses)
				.map(Via::parseSingle)
				.collect(Collectors.toList());
	}

	public static Via parseSingle(String str) {
		var sentProtocolAndSentParameters = str.trim().split("\\s+", 2);
		if (sentProtocolAndSentParameters.length < 2) {
			throw new IllegalArgumentException("Invalid Via value, expected via-params =  via-ttl / via-maddr");
		}
		var sipSentProtocol = SipSentProtocol.parse(sentProtocolAndSentParameters[0]);
		var parametersStartIndex = sentProtocolAndSentParameters[1].indexOf(';');
		if (parametersStartIndex == NOT_FOUND) {
			parametersStartIndex = sentProtocolAndSentParameters[1].length();
		}
		var sentBy = Address.parse(sentProtocolAndSentParameters[1].substring(0, parametersStartIndex));
		var parameters = SipParseUtils.parseParameters(sentProtocolAndSentParameters[1].substring(parametersStartIndex), PARAMETERS_DELIMITER);
		return new Via(sipSentProtocol, sentBy, parameters);
	}

}
