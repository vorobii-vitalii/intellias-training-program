package websocket.reader;

import tcp.server.reader.exception.ParseException;
import org.junit.jupiter.api.Test;
import utils.BufferTestUtils;
import websocket.domain.OpCode;

import java.math.BigInteger;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketMessageReaderTest {
	private static final byte[] MASKING_KEY = {1, 122, 33, 48};
	private static final Random RANDOM = new Random();

	private final WebSocketMessageReader webSocketMessageReader = new WebSocketMessageReader();

	@Test
	void readGivenOnly1ByteRead() throws ParseException {
		assertThat(webSocketMessageReader.read(BufferTestUtils.createBufferContext(new byte[]{1}), e -> {})).isNull();
	}

	@Test
	void readGivenMaskedMessageWithSmallSizeWithNoPayloadAvailable() throws ParseException {
		var res = webSocketMessageReader.read(BufferTestUtils.createBufferContext(
						merge(
										new byte[]{
														// Fin = true, OPCODE = 1 (TEXT)
														(byte) 0b10000001,
														// Masked = true, Payload Size = 1 + 2 + 4 = 7 bytes
														(byte) 0b10000111
										},
										MASKING_KEY
						)
		), e -> {});
		assertThat(res).isNull();
	}

	@Test
	void readGivenMaskedMessageWithSmallSizeWithPayloadAvailable() throws ParseException {
		byte[] payload = createRandomArr(7);
		var res = webSocketMessageReader.read(BufferTestUtils.createBufferContext(
						merge(
										new byte[]{
														// Fin = true, OPCODE = 1 (TEXT)
														(byte) 0b10000001,
														// Masked = true, Payload Size = 1 + 2 + 4 = 7 bytes
														(byte) 0b10000111
										},
										MASKING_KEY,
										mask(payload)
						)), e -> {});
		assertThat(res).isNotNull();
		assertThat(res.first().isFin()).isEqualTo(true);
		assertThat(res.first().getOpCode()).isEqualTo(OpCode.TEXT);
		assertThat(res.first().getMaskingKey()).isEqualTo(MASKING_KEY);
		assertThat(res.first().getPayload()).isEqualTo(payload);
	}

	@Test
	void readGivenNotMaskedMessageWithSmallSizeWithPayloadAvailable() throws ParseException {
		byte[] payload = createRandomArr(7);
		var res = webSocketMessageReader.read(BufferTestUtils.createBufferContext(
						merge(
										new byte[]{
														// Fin = true, OPCODE = 1 (TEXT)
														(byte) 0b10000001,
														// Masked = false, Payload Size = 1 + 2 + 4 = 7 bytes
														(byte) 0b00000111
										},
										payload
						)), e -> {});
		assertThat(res).isNotNull();
		assertThat(res.first().isFin()).isEqualTo(true);
		assertThat(res.first().getOpCode()).isEqualTo(OpCode.TEXT);
		assertThat(res.first().getMaskingKey()).isNull();
		assertThat(res.first().getPayload()).isEqualTo(payload);
	}

	@Test
	void readGivenMaskedMessageWithExtendedSizeWithPayloadAvailable() throws ParseException {
		byte[] payload = createRandomArr(15000);
		var res = webSocketMessageReader.read(BufferTestUtils.createBufferContext(
						merge(
										new byte[]{
														// Fin = true, OPCODE = 1 (TEXT)
														(byte) 0b10000001,
														// Masked = true, Payload Size = 126 (size is in next 2 bytes)
														(byte) 0b1_1111110
										},
										pad(BigInteger.valueOf(15000).toByteArray(), 2),
										MASKING_KEY,
										mask(payload)
						)), e -> {});
		assertThat(res).isNotNull();
		assertThat(res.first().isFin()).isEqualTo(true);
		assertThat(res.first().getOpCode()).isEqualTo(OpCode.TEXT);
		assertThat(res.first().getMaskingKey()).isEqualTo(MASKING_KEY);
		assertThat(res.first().getPayload()).isEqualTo(payload);
	}

	@Test
	void readGivenMaskedMessageWithHugeSizeWithPayloadAvailable() throws ParseException {
		byte[] payload = createRandomArr(120_000);
		var res = webSocketMessageReader.read(BufferTestUtils.createBufferContext(
						merge(
										new byte[]{
														// Fin = true, OPCODE = 1 (TEXT)
														(byte) 0b10000001,
														// Masked = true, Payload Size = 127 (size is in next 2 bytes)
														(byte) 0b1_1111111
										},
										pad(BigInteger.valueOf(120_000).toByteArray(), 8),
										MASKING_KEY,
										mask(payload)
						)), e -> {});
		assertThat(res).isNotNull();
		assertThat(res.first().isFin()).isEqualTo(true);
		assertThat(res.first().getOpCode()).isEqualTo(OpCode.TEXT);
		assertThat(res.first().getMaskingKey()).isEqualTo(MASKING_KEY);
		assertThat(res.first().getPayload()).isEqualTo(payload);
	}

	private byte[] createRandomArr(int size) {
		byte[] arr = new byte[size];
		RANDOM.nextBytes(arr);
		return arr;
	}

	private byte[] merge(byte[]... arrays) {
		int total = 0;
		for (byte[] array : arrays) {
			total += array.length;
		}
		byte[] res = new byte[total];
		int start = 0;
		for (byte[] array : arrays) {
			System.arraycopy(array, 0, res, start, array.length);
			start += array.length;
		}
		return res;
	}

	private byte[] mask(byte[] arr) {
		byte[] masked = new byte[arr.length];
		for (int i = 0; i < arr.length; i++) {
			masked[i] = (byte) (MASKING_KEY[i % MASKING_KEY.length] ^ arr[i]);
		}
		return masked;
	}

	private byte[] pad(byte[] arr, int k) {
		byte[] res = new byte[k];
		for (int i = 0; i < arr.length; i++) {
			if (arr[arr.length - i - 1] != 0) {
				res[res.length - i - 1] = arr[arr.length - i - 1];
			}
		}
		return res;
	}


}