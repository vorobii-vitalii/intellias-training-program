package util;

import java.nio.ByteBuffer;

public interface Serializable {
	@Deprecated
	byte[] serialize();

	void serialize(ByteBuffer dest);

	int getSize();

}
