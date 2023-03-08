package http;

import server.ServerOperationType;
import server.TCPServer;
import server.TCPServerConfig;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class HTTPServer {
    private static final int BUFFER_CAPACITY = 1000;
    private static final int PORT = 8000;
    private static final String HOSTNAME = "127.0.0.1";
    public static final String CONTENT_LENGTH = "content-length";

    enum State {
        REQUEST_LINE,
        HEADERS,
        BODY,
        FINAL
    }

    static class HTTPRequest {
        public String requestLine;
        public Map<String, List<String>> headers = new HashMap<>();
        public byte[] body;
        public int bodyIndex = -1;

        public State calcState() {
            if (requestLine == null) {
                return State.REQUEST_LINE;
            }
            if (bodyIndex == -1) {
                return State.HEADERS;
            }
            return bodyIndex == body.length ? State.FINAL : State.BODY;
        }

        @Override
        public String toString() {
            return "HTTPRequest{" +
                    "requestLine='" + requestLine + '\'' +
                    ", headers=" + headers +
                    ", body=" + Arrays.toString(body) +
                    ", bodyIndex=" + bodyIndex +
                    '}';
        }
    }

    static class HTTPResponse {
        public String httpVersion;
        public int statusCode;
        public String message;
        public Map<String, List<String>> headers = new HashMap<>();
        public byte[] body = new byte[0];

        @Override
        public String toString() {
            return "HTTPResponse{" +
                    "httpVersion='" + httpVersion + '\'' +
                    ", statusCode=" + statusCode +
                    ", reasonPhrase='" + message + '\'' +
                    ", headers=" + headers +
                    ", body=" + Arrays.toString(body) +
                    '}';
        }

        public byte[] serialize() {
            var responseLine = ("HTTP/" + httpVersion + " " + statusCode + " " + message + "\r\n").getBytes(StandardCharsets.US_ASCII);
            var headers = (this.headers.entrySet().stream()
                    .map(e -> {
                        String value = String.join(" ", e.getValue());
                        String key = e.getKey();
                        return key + ":" + value;
                    })
                    .collect(Collectors.joining("\r\n", "", "\r\n\r\n"))).getBytes(StandardCharsets.US_ASCII);
            return merge(responseLine, headers, body);
        }

        private byte[] merge(byte[] responseLine, byte[] headers, byte[] body) {
            int totalSize = responseLine.length + headers.length + body.length;
            byte[] arr = new byte[totalSize];
            System.arraycopy(responseLine, 0, arr, 0, responseLine.length);
            System.arraycopy(headers, 0, arr, responseLine.length, headers.length);
            System.arraycopy(body, 0, arr, responseLine.length + headers.length, body.length);
            return arr;
        }
    }

    public static class AttachmentObject {
        public ByteBuffer readBuffer;
        public ByteBuffer writeBuffer;
        public StringBuilder stringBuilder;
        public HTTPRequest httpRequest;

        public AttachmentObject(ByteBuffer readBuffer, ByteBuffer writeBuffer, StringBuilder stringBuilder, HTTPRequest httpRequest) {
            this.readBuffer = readBuffer;
            this.writeBuffer = writeBuffer;
            this.stringBuilder = stringBuilder;
            this.httpRequest = httpRequest;
        }
    }

    public static void main(String[] args) {

        var server = new TCPServer(
                TCPServerConfig.builder()
                        .setHost(HOSTNAME)
                        .setPort(PORT)
                        .setProtocolFamily(StandardProtocolFamily.INET)
                        .build(),
                SelectorProvider.provider(),
                System.err::println,
                Map.of(
                    ServerOperationType.ACCEPT, selectionKey -> {
                        try {
                            var socketChannel = ((ServerSocketChannel)selectionKey.channel()).accept();
                            var selector = selectionKey.selector();
                            System.out.println("Client connected " + socketChannel);
                            socketChannel.configureBlocking(false);
                            var readBuffer = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
                            readBuffer.position(readBuffer.limit());
                            socketChannel.register(
                                    selector,
                                    SelectionKey.OP_READ,
                                    new AttachmentObject(
                                            readBuffer,
                                            null,
                                            new StringBuilder(),
                                            new HTTPRequest()
                                    )
                            );
                        } catch (IOException e) {
                            e.printStackTrace();
                            selectionKey.cancel();
                        }
                    },
                    ServerOperationType.READ, selectionKey -> {
                        var socketChannel = (SocketChannel) (selectionKey.channel());
                        try {
                            AttachmentObject attachment = (AttachmentObject) selectionKey.attachment();
                            do {
                                doHandleRead(selectionKey, socketChannel);
                            }
                            while (attachment.readBuffer.hasRemaining() && attachment.httpRequest.calcState() != State.FINAL);
                        } catch (IOException e) {
                            e.printStackTrace();
                            selectionKey.cancel();
                        }
                    },
                    ServerOperationType.WRITE, selectionKey -> {
                        var socketChannel = (SocketChannel) (selectionKey.channel());
                        AttachmentObject attachment = (AttachmentObject) selectionKey.attachment();
                        try {
                            ByteBuffer writeBuffer = attachment.writeBuffer;
                            System.out.println("Writing response " + writeBuffer);
                            while (attachment.writeBuffer.hasRemaining()) {
                                int bytesWritten = socketChannel.write(writeBuffer);
                                if (bytesWritten == 0) {
                                    break;
                                }
                            }
                            if (!writeBuffer.hasRemaining()) {
                                System.out.println("No remaining left");
                                attachment.httpRequest = new HTTPRequest();
                                attachment.writeBuffer = null;
                                selectionKey.interestOps(SelectionKey.OP_READ);
                                do {
                                    doHandleRead(selectionKey, socketChannel);
                                }
                                while (attachment.readBuffer.hasRemaining() && attachment.httpRequest.calcState() != State.FINAL);
                            } else {
                                System.out.println("has remaining");
                                selectionKey.interestOps(SelectionKey.OP_WRITE);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            selectionKey.cancel();
                        }
                    }
                ));

        server.start();
    }

    private static void doHandleRead(SelectionKey selectionKey, SocketChannel socketChannel) throws IOException {
        var attachment = (AttachmentObject) selectionKey.attachment();
        State state = attachment.httpRequest.calcState();
        if (state == State.REQUEST_LINE) {
            var requestLine = readLine(attachment.readBuffer, socketChannel, attachment.stringBuilder);
            if (requestLine != null) {
                System.out.println("Request line = " + requestLine);
                attachment.httpRequest.requestLine = requestLine;
            }
        } else if (state == State.HEADERS) {
            var line = readLine(attachment.readBuffer, socketChannel, attachment.stringBuilder);
            System.out.println("Line = " + line);
            if (line != null) {
                if (line.isEmpty()) {
                    var bodySize = Optional.ofNullable(attachment.httpRequest.headers.get(CONTENT_LENGTH))
                            .map(list -> list.get(0))
                            .map(String::trim)
                            .map(Integer::parseInt)
                            .orElse(0);
                    // TODO: Add support of transfer-encoding
//                    List<String> encodings = attachment.httpRequest.headers.get("transfer-encoding");
                    if (bodySize == 0) {
                        System.out.println("Request: " + attachment.httpRequest);
                        var response = duplicate(attachment.httpRequest);
                        System.out.println("Response = " + response);
                        attachment.writeBuffer = ByteBuffer.wrap(response.serialize());
                        selectionKey.interestOps(SelectionKey.OP_WRITE);
                        return;
                    } else {
                        System.out.println("body size = " + bodySize);
                        attachment.httpRequest.body = new byte[bodySize];
                        attachment.httpRequest.bodyIndex = 0;
                    }
                }
                else {
                    String[] pair = line.split(":");
                    String[] arr = pair[1].split("[\t ]+");
                    // TODO: Check if ends with space! throw error in this case
                    String headerName = pair[0].toLowerCase();
                    if (!attachment.httpRequest.headers.containsKey(headerName)) {
                        attachment.httpRequest.headers.put(headerName, new ArrayList<>());
                    }
                    var list = attachment.httpRequest.headers.get(headerName);
                    for (var value : arr) {
                        if (!value.isEmpty()) {
                            list.add(value);
                        }
                    }
                }
            }
        } else {
             System.out.println("Reading body current = " + attachment.httpRequest);
            if (readByteArray(attachment.readBuffer, socketChannel, attachment.httpRequest)) {
                System.out.println("Request: " + attachment.httpRequest);
                HTTPResponse response = duplicate(attachment.httpRequest);
                System.out.println("Response = " + response);
                attachment.writeBuffer = ByteBuffer.wrap(response.serialize());
                selectionKey.interestOps(SelectionKey.OP_WRITE);
                return;
            }
             System.out.println("after reading body current = " + attachment.httpRequest);
        }
        selectionKey.interestOps(SelectionKey.OP_READ);
    }

    private static HTTPResponse duplicate(HTTPRequest httpRequest) {
        HTTPResponse httpResponse = new HTTPResponse();
        httpResponse.httpVersion = "1.1";
        httpResponse.body = ("{\"name\": \"" + httpRequest.requestLine + "\"}").getBytes(StandardCharsets.US_ASCII);
        httpResponse.headers = Map.of("Content-Type", List.of("application/json"), "Content-Length", List.of(String.valueOf(httpResponse.body.length)));
        httpResponse.message = "OK";
        httpResponse.statusCode = 200;
        return httpResponse;
    }

    private static String readLine(
            ByteBuffer buffer,
            SocketChannel socketChannel,
            StringBuilder stringBuilder
    ) throws IOException {
        if (readFromBuffer(buffer, stringBuilder)) {
            String res = stringBuilder.toString();
            stringBuilder.setLength(0);
            return res;
        }
        buffer.clear();
        socketChannel.read(buffer);
        buffer.flip();
        if (readFromBuffer(buffer, stringBuilder)) {
            String res = stringBuilder.toString();
            stringBuilder.setLength(0);
            return res;
        }
        return null;
    }

    private static boolean readByteArray(ByteBuffer buffer, SocketChannel socketChannel, HTTPRequest httpRequest) throws IOException {
        if (readFromBuffer(buffer, httpRequest)) {
            return true;
        }
        buffer.clear();
        socketChannel.read(buffer);
        buffer.flip();
        return readFromBuffer(buffer, httpRequest);
    }

    private static boolean readFromBuffer(ByteBuffer buffer, HTTPRequest httpRequest) {
        while (httpRequest.bodyIndex < httpRequest.body.length && buffer.hasRemaining()) {
            httpRequest.body[httpRequest.bodyIndex++] = buffer.get();
        }
        return httpRequest.bodyIndex == httpRequest.body.length;
    }

    private static boolean readFromBuffer(ByteBuffer buffer, StringBuilder stringBuilder) {
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == (byte) '\n' && isStartOfCLRFDetected(stringBuilder)) {
                stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                return true;
            }
            stringBuilder.append((char) b);
        }
        return false;
    }

    private static boolean isStartOfCLRFDetected(StringBuilder stringBuilder) {
        return !stringBuilder.isEmpty() && stringBuilder.charAt(stringBuilder.length() - 1) == (byte) '\r';
    }
}
