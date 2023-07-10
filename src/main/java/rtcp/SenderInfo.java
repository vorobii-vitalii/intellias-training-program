package rtcp;

public record SenderInfo(int senderSSRC, long ntpTimestamp, int rtpTimestamp, int senderPacketCount, int senderOctecCount) {
}
