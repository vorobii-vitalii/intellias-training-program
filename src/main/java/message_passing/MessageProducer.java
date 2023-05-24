package message_passing;

public interface MessageProducer<E> {
	void produce(E event);
}
