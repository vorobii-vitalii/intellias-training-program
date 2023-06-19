package util;

import java.nio.ByteBuffer;

public interface Serializable {
	void serialize(ByteBuffer dest);

	int getSize();
}
