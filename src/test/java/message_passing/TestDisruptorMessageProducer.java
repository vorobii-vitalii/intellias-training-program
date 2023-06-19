package message_passing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lmax.disruptor.RingBuffer;

import document_editor.utils.Copyable;

@ExtendWith(MockitoExtension.class)
class TestDisruptorMessageProducer {

	public static final long SEQ_ID = 123L;
	@Mock
	private RingBuffer<CopyableImpl> ringBuffer;

	@InjectMocks
	DisruptorMessageProducer<CopyableImpl> disruptorMessageProducer;

	@Test
	void produce() {
		when(ringBuffer.next()).thenReturn(SEQ_ID);
		var copyable = new CopyableImpl();
		when(ringBuffer.get(SEQ_ID)).thenReturn(copyable);
		disruptorMessageProducer.produce(new CopyableImpl(25));
		assertThat(copyable).isEqualTo(new CopyableImpl(25));
		verify(ringBuffer).publish(SEQ_ID);
	}

	static final class CopyableImpl implements Copyable<CopyableImpl> {
		private int v;

		public CopyableImpl() {

		}

		public CopyableImpl(int v) {
			this.v = v;
		}

		public void setV(int v) {
			this.v = v;
		}

		@Override
		public void copy(CopyableImpl obj) {
			this.setV(obj.v);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			CopyableImpl copyable = (CopyableImpl) o;
			return v == copyable.v;
		}

		@Override
		public int hashCode() {
			return Objects.hash(v);
		}

		@Override
		public String toString() {
			return "CopyableImpl{" +
					"v=" + v +
					'}';
		}
	}


}