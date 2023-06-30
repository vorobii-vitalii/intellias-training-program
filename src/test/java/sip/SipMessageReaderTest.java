package sip;

import org.junit.jupiter.api.Test;
import tcp.server.reader.exception.ParseException;
import utils.BufferTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SipMessageReaderTest {

	SipMessageReader sipMessageReader = new SipMessageReader();

	@Test
	void readHappyPath() throws ParseException {
		var bytes = ("INVITE sip:bob@biloxi.com SIP/2.0\r\n" +
						"      Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bK776asdhds\r\n" +
						"      Max-Forwards: 70\r\n" +
						"      To: Bob <sip:bob@biloxi.com>\r\n" +
						"      From: Alice <sip:alice@atlanta.com>;tag=1928301774\r\n" +
						"      Call-ID: a84b4c76e66710@pc33.atlanta.com\r\n" +
						"      CSeq: 314159 INVITE\r\n" +
						"      Contact: <sip:alice@pc33.atlanta.com>\r\n" +
						"      Content-Type: application/sdp\r\n" +
						"      Content-Length: 0\r\n\r\n"
		).getBytes(StandardCharsets.UTF_8);

		var readResult = sipMessageReader.read(BufferTestUtils.createBufferContext(bytes), e -> {});
		assertThat(readResult).isNotNull();
		SipRequestHeaders sipRequestHeaders = new SipRequestHeaders();
		sipRequestHeaders.addVia(new Via(
						new SipSentProtocol("SIP", "2.0", "UDP"),
						new Address("pc33.atlanta.com", null),
						Map.of("branch", "z9hG4bK776asdhds")
		));
		sipRequestHeaders.setMaxForwards(70);
		sipRequestHeaders.setFrom(new AddressOfRecord(
						"Alice",
						new FullSipURI(
										"sip",
										new Credentials("alice", null),
										new Address("atlanta.com", 5060),
										Map.of(),
										Map.of(),
								"sip:alice@atlanta.com"
						),
						Map.of("tag", "1928301774")
		));
		sipRequestHeaders.setTo(new AddressOfRecord(
						"Bob",
						new FullSipURI(
										"sip",
										new Credentials("bob", null),
										new Address("biloxi.com", 5060),
										Map.of(),
										Map.of(),
								"sip:bob@biloxi.com"
						),
						Map.of()
		));
		sipRequestHeaders.addSingleHeader("Call-ID", "a84b4c76e66710@pc33.atlanta.com");
		sipRequestHeaders.setContentType(new SipMediaType("application", "sdp", Map.of()));
		sipRequestHeaders.setCommandSequence(new CommandSequence(314159, "INVITE"));
		sipRequestHeaders.setContactList(new ContactSet(Set.of(
				new AddressOfRecord(
						"Anonymous",
						new FullSipURI(
								"sip",
								new Credentials("alice", null),
								new Address("pc33.atlanta.com", 5060),
								Map.of(),
								Map.of(),
								"sip:alice@pc33.atlanta.com"
						),
						Map.of()
				)
		)));

		assertThat(readResult.first()).isEqualTo(
						new SipRequest(
										new SipRequestLine(
														"INVITE",
														new FullSipURI(
																		"sip",
																		new Credentials("bob", null),
																		new Address("biloxi.com", 5060),
																		Map.of(),
																		Map.of(),
																"sip:bob@biloxi.com"
														),
														new SipVersion(2, 0)
										),
								sipRequestHeaders,
										new byte[]{}
						));

		assertThat(readResult.second()).isEqualTo(bytes.length);
	}
}