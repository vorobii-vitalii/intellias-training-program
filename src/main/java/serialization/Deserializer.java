package serialization;

import java.io.IOException;
import java.io.InputStream;

public interface Deserializer {
	<T> T deserialize(InputStream inputStream, Class<T> clz) throws IOException;
}
