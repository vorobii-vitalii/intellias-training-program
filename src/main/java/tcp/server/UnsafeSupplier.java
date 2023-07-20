package tcp.server;

@FunctionalInterface
public interface UnsafeSupplier<T> {
	T get() throws Exception;
}
