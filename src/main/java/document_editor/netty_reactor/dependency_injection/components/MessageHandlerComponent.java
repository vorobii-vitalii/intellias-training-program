package document_editor.netty_reactor.dependency_injection.components;

import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.BindsInstance;
import dagger.Component;
import document_editor.dto.ClientRequest;
import document_editor.dto.RequestType;
import document_editor.dto.Response;
import document_editor.netty_reactor.dependency_injection.constants.Constants;
import document_editor.netty_reactor.dependency_injection.modules.MessageHandlerModule;
import request_handler.ReactiveMessageHandler;

@Component(modules = MessageHandlerModule.class)
@Singleton
public interface MessageHandlerComponent {
	Set<ReactiveMessageHandler<RequestType, ClientRequest, Response, Object>> getMessageHandlers();

	@Component.Builder
	interface Builder {
		@BindsInstance
		Builder withDocumentStorageServiceURI(@Named(Constants.DOCUMENT_STORAGE_SERVICE_URI) String documentStorageServiceURI);
		MessageHandlerComponent build();
	}
}
