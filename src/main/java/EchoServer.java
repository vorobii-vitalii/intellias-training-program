import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.*;

public class EchoServer {
    public static final int BUFFER_CAPACITY = 1000;
    private static final int PORT = 8000;
    public static final String HOSTNAME = "127.0.0.1";

    public static void main(String[] args) throws IOException {
        try (var selector = Selector.open();
             var serverSocketChannel = ServerSocketChannel.open(StandardProtocolFamily.INET)
        ) {
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            serverSocketChannel.bind(new InetSocketAddress(HOSTNAME, PORT));
            while (true) {
                selector.select(selectionKey -> {
                    if (!selectionKey.isValid()) {
                        return;
                    }
                    // Accept new connection
                    if (selectionKey.isAcceptable()) {
                        var buffer = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
                        try {
                            var socketChannel = serverSocketChannel.accept();
                            socketChannel.configureBlocking(false);
                            socketChannel.register(selector, SelectionKey.OP_READ, buffer);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    // Read from socket
                    else if (selectionKey.isReadable()) {
                        var socketChannel = (SocketChannel) (selectionKey.channel());
                        var buffer = (ByteBuffer) (selectionKey.attachment());
                        try {
                            socketChannel.read(buffer);
                            buffer.flip();
                            selectionKey.interestOps(SelectionKey.OP_WRITE);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    // Write to socket
                    else if (selectionKey.isWritable()) {
                        var socketChannel = (SocketChannel) (selectionKey.channel());
                        var buffer = (ByteBuffer) (selectionKey.attachment());
                        try {
                            socketChannel.write(buffer);
                            buffer.clear();
                            selectionKey.interestOps(SelectionKey.OP_READ);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
        }
    }

}
