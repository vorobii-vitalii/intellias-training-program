package serialization;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JacksonDeserializer implements Deserializer {
	private final ObjectMapper objectMapper;

	public JacksonDeserializer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public <T> T deserialize(InputStream inputStream, Class<T> clz) throws IOException {
		return objectMapper.readValue(inputStream, clz);
	}
}
