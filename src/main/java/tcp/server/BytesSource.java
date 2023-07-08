package tcp.server;

public interface BytesSource {
	byte[] extract(int from, int end);

	byte get(int pos);
	int size();
}
