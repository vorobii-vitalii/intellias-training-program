package tcp;

import tcp.server.BytesSource;

public class CharSequenceImpl implements CharSequence {
    private final BytesSource bytesSource;
    private final int from;
    private final int end;
    private String string;

    public CharSequenceImpl(BytesSource bytesSource, int from, int end) {
        this.bytesSource = bytesSource;
        this.from = from;
        this.end = end;
    }

    @Override
    public int length() {
        return end - from;
    }

    @Override
    public char charAt(int index) {
        return (char) bytesSource.get(from + index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new CharSequenceImpl(bytesSource, from + start, from + end);
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
        if (string == null) {
            this.string = new String(bytesSource.extract(from, end));
        }
        return string;
    }
}
