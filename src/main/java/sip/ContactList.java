package sip;

import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import util.Serializable;

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
public interface ContactList extends Serializable {
	String STAR = "*";
	char COMMA = ',';

	Map<Character, Character> MAP = Map.of(
			'<', '>',
			'"', '"',
			'\'', '\''
	);

	boolean shouldCall(AddressOfRecord addressOfRecord);

	static ContactList parse(String str) {
		var trimmed = str.trim();
		if (STAR.equals(trimmed)) {
			return new ContactAny();
		}
		var deque = new LinkedList<Character>();
		var previousIndex = 0;
		Set<AddressOfRecord> addressOfRecords = new HashSet<>();
		for (var i = 0; i < trimmed.length(); i++) {
			var c = trimmed.charAt(i);
			// TODO: Create more "generic" function
			if (COMMA == c) {
				// Can parse one of addresses
				if (deque.isEmpty()) {
					addressOfRecords.add(AddressOfRecord.parse(str.substring(previousIndex, i)));
					previousIndex = i + 1;
				}
			}
			else {
				if (!deque.isEmpty() && MAP.get(deque.peekLast()) == c) {
					deque.removeLast();
				} else if (MAP.containsKey(c)) {
					deque.addLast(c);
				}
			}
		}
		addressOfRecords.add(AddressOfRecord.parse(trimmed.substring(previousIndex, trimmed.length())));
		return new ContactSet(addressOfRecords);
	}
}
