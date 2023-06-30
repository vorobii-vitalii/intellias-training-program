package sip;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestAddressOfRecord {
	private static final String ANONYMOUS = "Anonymous";

	public static Object[][] parseTestParameters() {
		return new Object[][] {
				{
						"<sip:c8oqz84zk7z@privacy.org>;tag=hyh8",
						new AddressOfRecord(
								ANONYMOUS,
								new FullSipURI(
										"sip",
										new Credentials("c8oqz84zk7z", null),
										new Address("privacy.org", 5060),
										Map.of(),
										Map.of(),
										"sip:c8oqz84zk7z@privacy.org>;tag=hyh8"),
								Map.of("tag", "hyh8")
						)
				},
				{
						"Williams <sip:c8oqz84zk7z@privacy.org;param=val?querParam=val>;tag=hyh8",
						new AddressOfRecord(
								"Williams",
								new FullSipURI(
										"sip",
										new Credentials("c8oqz84zk7z", null),
										new Address("privacy.org", 5060),
										Map.of("param", "val"),
										Map.of("querParam", "val"),
										"sip:c8oqz84zk7z@privacy.org;param=val?querParam=val"),
								Map.of("tag", "hyh8")
						)
				},
				{
						"   \"Williams\"    <sip:c8oqz84zk7z@privacy.org;param=val?queryParam=val>  ;  tag=hyh8",
						new AddressOfRecord(
								"Williams",
								new FullSipURI(
										"sip",
										new Credentials("c8oqz84zk7z", null),
										new Address("privacy.org", 5060),
										Map.of("param", "val"),
										Map.of("queryParam", "val"),
										"sip:c8oqz84zk7z@privacy.org;param=val?queryParam=val"
								),
								Map.of("tag", "hyh8")
						)
				},
				{
						"   \"Williams\"    </absoluteURI>  ;  tag=hyh8",
						new AddressOfRecord(
								"Williams",
								new SIPAbsoluteURI("/absoluteURI"),
								Map.of("tag", "hyh8")
						)
				}
		};
	}

	@ParameterizedTest
	@MethodSource("parseTestParameters")
	void parse(String addressOfRecordStr, AddressOfRecord expectedRecord) {
		assertThat(AddressOfRecord.parse(addressOfRecordStr)).isEqualTo(expectedRecord);
	}
}
