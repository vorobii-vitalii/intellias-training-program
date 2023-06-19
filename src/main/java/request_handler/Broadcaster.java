package request_handler;

import java.util.List;

import message_passing.MessageProducer;

public class Broadcaster<RequestMessage> implements RequestHandler<RequestMessage> {
	private final List<? extends MessageProducer<RequestMessage>> messageProducers;

	public Broadcaster(List<? extends MessageProducer<RequestMessage>> messageProducers) {
		this.messageProducers = messageProducers;
	}

	@Override
	public void handle(RequestMessage request) {
		messageProducers.parallelStream().forEach(s -> s.produce(request));
	}
}
