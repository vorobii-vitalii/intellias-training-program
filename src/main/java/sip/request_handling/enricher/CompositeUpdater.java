package sip.request_handling.enricher;

import java.util.Collection;

import sip.request_handling.Updater;

public class CompositeUpdater<T> implements Updater<T> {
	private final Collection<Updater<T>> updaters;

	public CompositeUpdater(Collection<Updater<T>> updaters) {
		this.updaters = updaters;
	}

	@Override
	public T update(T prevObj) {
		return updaters.stream()
				.reduce(prevObj, (a, updater) -> updater.update(a), (a, b) -> b);
	}
}
