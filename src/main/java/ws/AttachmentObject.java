package ws;

import reader.MessageReader;

import java.nio.ByteBuffer;

public record AttachmentObject<T>(Mode<T> mode, ByteBuffer buffer, MessageReader<T> messageReader) {

}
