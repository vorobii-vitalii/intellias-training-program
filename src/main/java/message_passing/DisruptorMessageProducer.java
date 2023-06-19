package message_passing;

import com.lmax.disruptor.RingBuffer;

import document_editor.utils.Copyable;

public class DisruptorMessageProducer<T extends Copyable<T>> implements MessageProducer<T> {
	private final RingBuffer<T> ringBuffer;

	public DisruptorMessageProducer(RingBuffer<T> ringBuffer) {
		this.ringBuffer = ringBuffer;
	}

	@Override
	public void produce(T event) {
		var sequenceId = ringBuffer.next();
		var obj = ringBuffer.get(sequenceId);
		obj.copy(event);
		ringBuffer.publish(sequenceId);
	}
}
