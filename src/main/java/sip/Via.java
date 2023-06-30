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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import util.Serializable;

public record Via(SipSentProtocol sipSentProtocol, Address sentBy, Map<String, String> parameters) implements Serializable {
	public static final int PARAMETER_LIST_DELIMITER_LENGTH = 1;
	public static final int PARAMETER_DELIMITER_LENGTH = 1;
	public static final byte PARAMETERS_DELIMITER_CHAR = (byte) ';';
	public static final char PARAMETER_DELIMITER = '=';
	private static final int NOT_FOUND = -1;
	public static final String PARAMETERS_DELIMITER = ";";
	public static final int DELIMITER_LENGTH = 1;
	public static final byte SPACE = (byte) ' ';

	public Via normalize() {
		var newParameters = new LinkedHashMap<String, String>();
		newParameters.put("branch", parameters.get("branch"));
		return new Via(sipSentProtocol, sentBy, newParameters);
	}

	public Via addParameter(String param, String value) {
		var newParameters = new LinkedHashMap<>(parameters);
		newParameters.put(param, value);
		return new Via(sipSentProtocol, sentBy, newParameters);
	}

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

	@Override
	public void serialize(ByteBuffer dest) {
		sipSentProtocol.serialize(dest);
		dest.put(SPACE);
		sentBy.serialize(dest);
		serializeParameters(dest);
	}

	@Override
	public int getSize() {
		return sipSentProtocol.getSize() + DELIMITER_LENGTH + sentBy.getSize() + getParametersInBytes();
	}

	private void serializeParameters(ByteBuffer dest) {
		for (var entry : parameters.entrySet()) {
			dest.put(PARAMETERS_DELIMITER_CHAR);
			dest.put(entry.getKey().getBytes(StandardCharsets.UTF_8));
			dest.put((byte) PARAMETER_DELIMITER);
			dest.put(entry.getValue().getBytes(StandardCharsets.UTF_8));
		}
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
