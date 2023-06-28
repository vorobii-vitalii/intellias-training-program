package sip;

import java.util.Arrays;
import java.util.stream.Collectors;

//Contact        =  ("Contact" / "m" ) HCOLON
//( STAR / (contact-param *(COMMA contact-param)))
//contact-param  =  (name-addr / addr-spec) *(SEMI contact-params)
//name-addr      =  [ display-name ] LAQUOT addr-spec RAQUOT
//addr-spec      =  SIP-URI / SIPS-URI / absoluteURI
//display-name   =  *(token LWS)/ quoted-string
//
//contact-params     =  c-p-q / c-p-expires
/// contact-extension
//c-p-q              =  "q" EQUAL qvalue
//c-p-expires        =  "expires" EQUAL delta-seconds
//contact-extension  =  generic-param
//delta-seconds      =  1*DIGIT
public sealed interface ContactList permits ContactAny, ContactSet {
	String STAR = "*";
	String COMMA = ",";

	boolean shouldCall(AddressOfRecord addressOfRecord);

	static ContactList parse(String str) {
		var trimmed = str.trim();
		if (STAR.equals(trimmed)) {
			return new ContactAny();
		}
		return new ContactSet(Arrays.stream(trimmed.split(COMMA))
						.map(AddressOfRecord::parse)
						.collect(Collectors.toSet()));
	}
}
