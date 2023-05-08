package http.post_processor;

import http.domain.HTTPRequest;
import http.domain.HTTPResponse;
import http.protocol_change.ProtocolChangeContext;
import http.protocol_change.ProtocolChanger;
import request_handler.NetworkRequest;

import java.nio.channels.Selector;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ProtocolChangerHTTPResponsePostProcessor implements HTTPResponsePostProcessor {
	private final Map<String, ProtocolChanger> protocolChangerMap;

	public ProtocolChangerHTTPResponsePostProcessor(
			Collection<ProtocolChanger> protocolChangers
	) {
		this.protocolChangerMap = protocolChangers.stream()
						.collect(Collectors.toMap(ProtocolChanger::getProtocolName, Function.identity()));
	}

	@Override
	public void handle(NetworkRequest<HTTPRequest> httpNetworkRequest, HTTPResponse response) {
		if (response.isUpgradeResponse()) {
			var protocolChanger = protocolChangerMap.get(response.getUpgradeProtocol());
			if (protocolChanger == null) {
				throw new IllegalArgumentException("Not supported protocol " + response.getUpgradeProtocol());
			}
			protocolChanger.changeProtocol(new ProtocolChangeContext(
							httpNetworkRequest.request(),
							response,
							httpNetworkRequest.socketConnection()
			));
		}
	}
}
