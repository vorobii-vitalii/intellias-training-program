package sip.request_handling;

public interface Updater<T> {
	T update(T prevObj);
}
