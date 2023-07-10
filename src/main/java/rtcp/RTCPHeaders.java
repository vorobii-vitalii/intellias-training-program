package rtcp;

public record RTCPHeaders(int version, boolean isPadded, int length) {
}
