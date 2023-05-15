package tcp.server;

import net.jcip.annotations.NotThreadSafe;

import java.io.IOException;
import java.io.InputStream;

@NotThreadSafe
public class CompositeInputStream extends InputStream  {
    public static final int END_OF_FILE = -1;
    private final InputStream[] inputStreams;
    private int index = 0;

    public CompositeInputStream(InputStream... inputStreams) {
        this.inputStreams = inputStreams;
    }

    @Override
    public int read() throws IOException {
        while (index != inputStreams.length) {
            var inputStream = inputStreams[index];
            var b = inputStream.read();
            if (b != END_OF_FILE) {
                return b & 0xff;
            }
            index++;
        }
        return END_OF_FILE;
    }
}
