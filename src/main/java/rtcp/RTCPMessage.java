package rtcp;

public record RTCPMessage(RTCPHeaders headers, RTCPReport report) {
}
