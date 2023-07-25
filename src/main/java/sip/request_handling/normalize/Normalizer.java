package sip.request_handling.normalize;

import java.util.Collection;

public class Normalizer<T, C> {
	private final Collection<ObjectNormalizer<T, C>> normalizers;

	public Normalizer(Collection<ObjectNormalizer<T, C>> normalizers) {
		this.normalizers = normalizers;
	}

	public T normalize(T initial, C context) {
		return normalizers.stream().reduce(initial, (prev, normalizer) -> normalizer.normalize(prev, context), (a, b) -> b);
	}

}
