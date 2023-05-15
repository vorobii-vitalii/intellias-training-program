package websocket.domain;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum OpCode {
	CONTINUATION(0),
	RESERVED(-1),
	TEXT(1),
	BINARY(2),
	CONNECTION_CLOSE(8),
	PING(9),
	PONG(10);

	private static final Map<Integer, OpCode> OP_CODE_MAP = Arrays.stream(OpCode.values())
			.collect(Collectors.toMap(OpCode::getCode, Function.identity()));

	private final int code;

	OpCode(int code) {
		this.code = code;
	}

	public static OpCode getByCode(int code) {
		if (!OP_CODE_MAP.containsKey(code)) {
			return RESERVED;
		}
		return OP_CODE_MAP.get(code);
	}

	public int getCode() {
		return code;
	}

}
