package sip;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import util.Serializable;

public record CommandSequence(int sequenceNumber, String commandName) implements Serializable {

	public static final int DELIMITER_LENGTH = 1;
	public static final char DELIMITER = ' ';

	public static CommandSequence parse(CharSequence charSequence) {
		var arr = charSequence.toString().trim().split("\\s+");
		return new CommandSequence(Integer.parseInt(arr[0]), arr[1]);
	}

	@Override
	public void serialize(ByteBuffer dest) {
		dest.put(String.valueOf(sequenceNumber).getBytes(StandardCharsets.UTF_8));
		dest.put((byte) DELIMITER);
		dest.put(commandName.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public int getSize() {
		return String.valueOf(sequenceNumber).length() + DELIMITER_LENGTH + commandName.length();
	}
}
