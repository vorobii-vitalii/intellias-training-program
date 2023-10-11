package serialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class TestJacksonDeserializer {
	JacksonDeserializer jacksonDeserializer = new JacksonDeserializer(new ObjectMapper());

	@Test
	void deserialize() throws IOException {
		var json = """
				{
				 "name": "Alex",
				 "age": 22
				}
				""";
		var actualUser = jacksonDeserializer.deserialize(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), User.class);
		assertThat(actualUser).isEqualTo(new User("Alex", 22));
	}

	record User(String name, int age) {
	}

}