package sip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TestVia {

	public static Object[][] parseMultipleParameters() {
		return new Object[][] {
				{
						"SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bKnashds8",
						List.of(
								new Via(
										new SipSentProtocol("SIP", "2.0", "UDP"),
										new Address("pc33.atlanta.com", null),
										Map.of("branch", "z9hG4bKnashds8")
								)
						)
				},
				{
						"SIP/2.0/UDP server10.biloxi.com\n"
								+ "         ;branch=z9hG4bKnashds8;received=192.0.2.3",
						List.of(
								new Via(
										new SipSentProtocol("SIP", "2.0", "UDP"),
										new Address("server10.biloxi.com", null),
										Map.of("branch", "z9hG4bKnashds8", "received", "192.0.2.3")
								)
						),
				},
				{
						"SIP/2.0/UDP server10.biloxi.com;branch=z9hG4bKnashds8;received=192.0.2.3,"
								+ "SIP/2.0/TCP server10.biloxi2.com:1234;branch=z9hG4bKnashds2;received=192.0.2.4",
						List.of(
								new Via(
										new SipSentProtocol("SIP", "2.0", "UDP"),
										new Address("server10.biloxi.com", null),
										Map.of("branch", "z9hG4bKnashds8", "received", "192.0.2.3")
								),
								new Via(
										new SipSentProtocol("SIP", "2.0", "TCP"),
										new Address("server10.biloxi2.com", 1234),
										Map.of("branch", "z9hG4bKnashds2", "received", "192.0.2.4")
								)
						),
				}
		};
	}

	@ParameterizedTest
	@MethodSource("parseMultipleParameters")
	void parseMultiple(String str, List<Via> expected) {
		assertThat(Via.parseMultiple(str)).containsExactlyInAnyOrderElementsOf(expected);
	}
}
