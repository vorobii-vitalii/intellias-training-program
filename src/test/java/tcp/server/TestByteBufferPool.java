package tcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

class TestByteBufferPool {
	private static final int BUFFER_EXPIRATION_TIME_MILLIS = 10_000;

	private static final int N = 1000;

	ByteBufferPool byteBufferPool = new ByteBufferPool(ByteBuffer::allocate, BUFFER_EXPIRATION_TIME_MILLIS);

	@Test
	void allocateGivenPoolEmpty() {
		assertThat(byteBufferPool.allocate(N).capacity()).isEqualTo(N);
	}

	@Test
	void allocateGivenBufferWithExactSizeFound() {
		var byteBuffer = ByteBuffer.allocate(N);
		byteBufferPool.save(byteBuffer);
		assertThat(byteBufferPool.allocate(N)).isEqualTo(byteBuffer);
	}

	@Test
	void allocateGivenBufferWithHigherSizeFound() {
		var byteBuffer = ByteBuffer.allocate(N + 5);
		byteBufferPool.save(byteBuffer);
		assertThat(byteBufferPool.allocate(N)).isEqualTo(byteBuffer);
	}

	@Test
	void allocateGivenBufferWithLowerSizeFound() {
		var byteBuffer = ByteBuffer.allocate(N - 5);
		byteBufferPool.save(byteBuffer);
		assertThat(byteBufferPool.allocate(N).capacity()).isEqualTo(N);
	}

}
