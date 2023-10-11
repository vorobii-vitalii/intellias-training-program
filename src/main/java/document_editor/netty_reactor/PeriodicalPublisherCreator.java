package document_editor.netty_reactor;

import java.time.Duration;
import java.util.function.Supplier;

import reactor.core.publisher.Flux;

public class PeriodicalPublisherCreator<T> {
	private final int intervalMs;
	private final Supplier<T> supplier;

	public PeriodicalPublisherCreator(int intervalMs, Supplier<T> supplier) {
		this.intervalMs = intervalMs;
		this.supplier = supplier;
	}

	public Flux<T> create() {
		return Flux.interval(Duration.ofMillis(intervalMs))
				.map(v -> supplier.get());
	}

}
