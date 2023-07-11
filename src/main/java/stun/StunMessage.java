package stun;

// TODO: Implement parsing of payload...
public record StunMessage(int messageType, int messageLength, int magicCookie, byte[] transactionId, byte[] payload) {
}
