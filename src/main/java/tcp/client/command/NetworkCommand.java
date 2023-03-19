package tcp.client.command;

import java.io.IOException;

public interface NetworkCommand<R> {
	R execute() throws IOException;
}
