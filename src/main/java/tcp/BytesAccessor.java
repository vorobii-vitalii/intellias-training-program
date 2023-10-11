package tcp;

public interface BytesAccessor {
    byte get(int index);
    int size();
    BytesAccessor extract(int start, int end);
    String serialize();
}
