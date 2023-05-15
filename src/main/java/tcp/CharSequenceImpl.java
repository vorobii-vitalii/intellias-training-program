package tcp;

import tcp.server.BufferContext;

public class CharSequenceImpl implements CharSequence {
    private final BufferContext bufferContext;
    private final int from;
    private final int end;

    public CharSequenceImpl(BufferContext bufferContext, int from, int end) {
        this.bufferContext = bufferContext;
        this.from = from;
        this.end = end;
    }

    @Override
    public int length() {
        return end - from;
    }

    @Override
    public char charAt(int index) {
        return (char) bufferContext.get(from + index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new CharSequenceImpl(bufferContext, from + start, from + end);
    }

    @Override
    public boolean equals(Object obj) {
        return this.toString().equals(obj);
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        for (var i = 0; i < length(); i++) {
            builder.append(charAt(i));
        }
        return builder.toString();
    }
}
