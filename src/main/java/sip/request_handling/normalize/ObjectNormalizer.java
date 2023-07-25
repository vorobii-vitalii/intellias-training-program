package sip.request_handling.normalize;

public interface ObjectNormalizer<T, C> {
	T normalize(T message, C context);
}
