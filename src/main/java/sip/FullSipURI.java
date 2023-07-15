package sip;

import javax.annotation.Nonnull;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static sip.SipParseUtils.parseParameters;

import util.Serializable;

/*
SIP-URI          =  "sip:" [ userinfo ] hostport
                    uri-parameters [ headers ]
SIPS-URI         =  "sips:" [ userinfo ] hostport
                    uri-parameters [ headers ]
userinfo         =  ( user / telephone-subscriber ) [ ":" password ] "@"
user             =  1*( unreserved / escaped / user-unreserved )
user-unreserved  =  "&" / "=" / "+" / "$" / "," / ";" / "?" / "/"
password         =  *( unreserved / escaped /
                    "&" / "=" / "+" / "$" / "," )
hostport         =  host [ ":" port ]
host             =  hostname / IPv4address / IPv6reference
hostname         =  *( domainlabel "." ) toplabel [ "." ]
domainlabel      =  alphanum
                    / alphanum *( alphanum / "-" ) alphanum
toplabel         =  ALPHA / ALPHA *( alphanum / "-" ) alphanum

IPv4address    =  1*3DIGIT "." 1*3DIGIT "." 1*3DIGIT "." 1*3DIGIT
IPv6reference  =  "[" IPv6address "]"
IPv6address    =  hexpart [ ":" IPv4address ]
hexpart        =  hexseq / hexseq "::" [ hexseq ] / "::" [ hexseq ]
hexseq         =  hex4 *( ":" hex4)
hex4           =  1*4HEXDIG
port           =  1*DIGIT

   The BNF for telephone-subscriber can be found in RFC 2806 [9].  Note,
   however, that any characters allowed there that are not allowed in
   the user part of the SIP URI MUST be escaped.

uri-parameters    =  *( ";" uri-parameter)
uri-parameter     =  transport-param / user-param / method-param
                     / ttl-param / maddr-param / lr-param / other-param
transport-param   =  "transport="
                     ( "udp" / "tcp" / "sctp" / "tls"
                     / other-transport)
other-transport   =  token
user-param        =  "user=" ( "phone" / "ip" / other-user)
other-user        =  token
method-param      =  "method=" Method
ttl-param         =  "ttl=" ttl
maddr-param       =  "maddr=" host
lr-param          =  "lr"
other-param       =  pname [ "=" pvalue ]
pname             =  1*paramchar
pvalue            =  1*paramchar
paramchar         =  param-unreserved / unreserved / escaped
param-unreserved  =  "[" / "]" / "/" / ":" / "&" / "+" / "$"

headers         =  "?" header *( "&" header )
header          =  hname "=" hvalue
hname           =  1*( hnv-unreserved / unreserved / escaped )
hvalue          =  *( hnv-unreserved / unreserved / escaped )
hnv-unreserved  =  "[" / "]" / "/" / "?" / ":" / "+" / "$"


 */
public record FullSipURI(
		@Nonnull String protocol,
		@Nonnull Credentials credentials,
		@Nonnull Address address,
		@Nonnull Map<String, String> uriParameters,
		@Nonnull Map<String, String> queryParameters
) implements SipURI {

	/*
		The default port value is transport and scheme dependent.
		The default is 5060  for  sip: using UDP, TCP, or SCTP.
		The default is 5061 for sip: using TLS over TCP and sips: over TCP.
	 */
	private static final Map<String, Integer> DEFAULT_PORT_BY_PROTOCOL = Map.of("sip", 5060, "sips", 5061);
	private static final int PROTOCOL_INDEX = 1;
	private static final int USERNAME_INDEX = 2;
	private static final int PASSWORD_INDEX = 3;
	private static final int HOST_INDEX = 4;
	private static final int PORT_INDEX = 5;
	private static final int SIP_URI_PARAMETERS_INDEX = 6;
	private static final int SIP_QUERY_PARAMETERS_INDEX = 7;

	private static final Pattern SIP_URL_PATTERN = Pattern.compile(
			"^(sips?):(?:([^\\s>:@]+)(?::([^\\s@>]+))?@)?([\\w\\-.]+)(?::(\\d+))?((?:;[^\\s=?>;]+(?:=[^\\s?;]+)?)*)(?:\\?("
					+ "([^\\s&=>]+=[^\\s&=>]+)(&[^\\s&=>]+=[^\\s&=>]+)*))?$", Pattern.CASE_INSENSITIVE);
	public static final String URI_PARAMETERS_DELIMITER = ";";
	public static final String QUERY_PARAMETERS_DELIMITER = "&";

	public static boolean isSipURI(CharSequence charSequence) {
		return SIP_URL_PATTERN.matcher(charSequence).matches();
	}

	@Override
	public String getURIAsString() {
		return protocol + ":" + serializeCredentials() + serializeAddress() + serializeURIParameters() + serializeQueryParameters();
	}

	private String serializeAddress() {
		return address.asString();
	}

	private String serializeURIParameters() {
		return uriParameters.entrySet().stream()
				.map(e -> ";" + e.getKey() + "=" + e.getValue())
				.collect(Collectors.joining(""));
	}

	private String serializeQueryParameters() {
		if (queryParameters.isEmpty()) {
			return "";
		}
		return queryParameters.entrySet().stream()
				.map(e -> e.getKey() + "=" + e.getValue())
				.collect(Collectors.joining("&", "?", ""));
	}

	private String serializeCredentials() {
		return credentials.asString();
	}

	public static FullSipURI parse(String charSequence) {
		var matcher = SIP_URL_PATTERN.matcher(charSequence);
		if (!matcher.matches()) {
			throw new IllegalArgumentException(charSequence + " is not valid SIP URL!");
		}
		var protocol = matcher.group(PROTOCOL_INDEX);
		var credentials = new Credentials(matcher.group(USERNAME_INDEX), matcher.group(PASSWORD_INDEX));
		var host = matcher.group(HOST_INDEX);
		var port = Optional.ofNullable(matcher.group(PORT_INDEX))
				.map(Integer::parseInt)
				.orElseGet(() -> DEFAULT_PORT_BY_PROTOCOL.get(protocol));
		var uriParameters = parseParameters(matcher.group(SIP_URI_PARAMETERS_INDEX), URI_PARAMETERS_DELIMITER);
		var queryParameters = parseParameters(matcher.group(SIP_QUERY_PARAMETERS_INDEX), QUERY_PARAMETERS_DELIMITER);

		return new FullSipURI(protocol, credentials, new Address(host, port), uriParameters, queryParameters);
	}

	@Override
	public SipURI toCanonicalForm() {
		return new FullSipURI(protocol, credentials, address.toCanonicalForm(), Map.of(), Map.of());
	}
}
