package websocket.handler;

import http.domain.*;
import http.handler.HTTPRequestHandlerStrategy;
import util.Constants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Predicate;

public class FileDownloadHTTPHandlerStrategy implements HTTPRequestHandlerStrategy {
	private final Predicate<String> pathPredicate;
	private final String fileName;
	private final String contentType;

	public FileDownloadHTTPHandlerStrategy(Predicate<String> pathPredicate, String fileName, String contentType) {
		this.pathPredicate = pathPredicate;
		this.fileName = fileName;
		this.contentType = contentType;
	}

	@Override
	public boolean supports(HTTPRequest httpRequest) {
		return pathPredicate.test(httpRequest.getHttpRequestLine().path());
	}

	@Override
	public HTTPResponse handleRequest(HTTPRequest request) {
		var outputStream = new ByteArrayOutputStream();
		try (var inputStream = this.getClass().getClassLoader().getResourceAsStream(fileName)) {
			int t;
			while ((t = inputStream.read()) != -1) {
				outputStream.write(t);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return new HTTPResponse(
						new HTTPResponseLine(
										new HTTPVersion(1, 1),
										Constants.HTTPStatusCode.OK,
										"OK"
						),
						new HTTPHeaders()
										.addSingleHeader(Constants.HTTPHeaders.CONTENT_LENGTH, String.valueOf(outputStream.size()))
										.addSingleHeader(Constants.HTTPHeaders.CONTENT_TYPE, contentType),
						outputStream.toByteArray()
		);
	}

	@Override
	public int getPriority() {
		return 0;
	}
}
