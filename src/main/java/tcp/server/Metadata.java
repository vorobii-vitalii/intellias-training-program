package tcp.server;

public interface Metadata {
	void setMetadata(String key, Object value);
	String getMetadata(String key);
}
