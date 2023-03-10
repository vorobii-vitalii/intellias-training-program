package echo;

import java.io.IOException;

public interface EchoConnection extends AutoCloseable {
	String sendMessage(String message) throws IOException;
}
