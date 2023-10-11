package request_handler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import message_passing.MessageProducer;

@ExtendWith(MockitoExtension.class)
class TestHashBasedLoadBalancer {

	@Mock
	MessageProducer<Integer> messageProducer1;

	@Mock
	MessageProducer<Integer> messageProducer2;

	HashBasedLoadBalancer<Integer> hashBasedLoadBalancer;

	@BeforeEach
	void init() {
		hashBasedLoadBalancer = new HashBasedLoadBalancer<>(v -> v, List.of(messageProducer1, messageProducer2));
	}

	@Test
	void handleGivenEvenHash() {
		hashBasedLoadBalancer.handle(2);
		verify(messageProducer1).produce(2);
		verifyNoInteractions(messageProducer2);
	}

	@Test
	void handleGivenOddHash() {
		hashBasedLoadBalancer.handle(3);
		verify(messageProducer2).produce(3);
		verifyNoInteractions(messageProducer1);
	}

}
