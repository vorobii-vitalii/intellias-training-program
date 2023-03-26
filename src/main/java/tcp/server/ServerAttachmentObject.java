package tcp.server;

import reader.MessageReader;
import writer.MessageWriter;

import java.nio.ByteBuffer;


public record ServerAttachmentObject<T>(
				String protocol,
				ByteBuffer readBuffer,
				MessageReader<T> messageReader,
				MessageWriter messageWriter
) {

}
