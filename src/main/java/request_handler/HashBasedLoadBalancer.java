package request_handler;

import java.util.List;
import java.util.function.Function;

import message_passing.MessageProducer;

public class HashBasedLoadBalancer<RequestMessage> implements RequestHandler<RequestMessage> {
	public static final int BITMASK = 0x7fffffff;
	private final List<? extends MessageProducer<RequestMessage>> messageProducers;
	private final Function<RequestMessage, Integer> hashFunction;

	public HashBasedLoadBalancer(
			Function<RequestMessage, Integer> hashFunction,
			List<? extends MessageProducer<RequestMessage>> messageProducers
	) {
		this.messageProducers = messageProducers;
		this.hashFunction = hashFunction;
	}

	@Override
	public void handle(RequestMessage requestMessage) {
		var hash = hashFunction.apply(requestMessage) & BITMASK;
		messageProducers.get(hash % messageProducers.size()).produce(requestMessage);
	}
}
