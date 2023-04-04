package http.handler;

import http.HTTPRequest;
import http.HTTPResponse;

import java.nio.channels.SelectionKey;

public record ProtocolChangeContext(HTTPRequest request, HTTPResponse response, SelectionKey selectionKey) {

}
