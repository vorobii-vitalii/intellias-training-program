package document_editor.netty_reactor.dto;

public record PingSubscribeEvent(String subscriptionId, boolean createOrRemove) {
}
