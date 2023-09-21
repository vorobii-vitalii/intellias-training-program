package sip.reactor_netty.dto;

import java.util.Map;

public record OnNotifyResponse(Map<String, String> sdpAnswerBySipURI) {
}
