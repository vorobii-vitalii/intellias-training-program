package sip;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TestFullSipURI {

	public static Object[][] testParameters() {
		return new Object[][] {
				{
						"sip:alice@example.com:5060;transport=tcp;user=phone?subject=hello&foo=bar",
						new FullSipURI(
								"sip",
								new Credentials("alice", null),
								new Address("example.com", 5060),
								Map.of("transport", "tcp", "user", "phone"),
								Map.of("subject", "hello", "foo", "bar")
						)
				},
				{
						"sip:alice@example.com:5060;transport=tcp;user=phone",
						new FullSipURI(
								"sip",
								new Credentials("alice", null),
								new Address("example.com", 5060),
								Map.of("transport", "tcp", "user", "phone"),
								Map.of()
						)
				},
				{
						"sip:alice@example.com;transport=tcp;user=phone",
						new FullSipURI(
								"sip",
								new Credentials("alice", null),
								new Address("example.com", 5060),
								Map.of("transport", "tcp", "user", "phone"),
								Map.of()
						)
				},
				{
						"sip:alice:password@example.com;transport=tcp;user=phone",
						new FullSipURI(
								"sip",
								new Credentials("alice", "password"),
								new Address("example.com", 5060),
								Map.of("transport", "tcp", "user", "phone"),
								Map.of()
						)
				}
		};
	}

	@ParameterizedTest
	@MethodSource("testParameters")
	void parse(String url, FullSipURI expected) {
		assertThat(FullSipURI.parse(url)).isEqualTo(expected);
	}
}