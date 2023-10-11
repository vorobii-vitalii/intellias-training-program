package document_editor.netty_reactor.dependency_injection.modules;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.inject.Singleton;

import com.example.document.storage.RxDocumentStorageServiceGrpc;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import document_editor.dto.ClientRequest;
import document_editor.dto.RequestType;
import document_editor.dto.Response;
import document_editor.netty_reactor.ReactiveDocumentChangesPublisher;
import document_editor.netty_reactor.request_handling.impl.ConnectReactiveRequestHandler;
import document_editor.netty_reactor.request_handling.impl.EditDocumentReactiveRequestHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import request_handler.ReactiveMessageHandler;
import request_handler.impl.CountingReactiveMessageHandler;

@Module(includes = { DocumentStorageServiceModule.class, MetricsModule.class })
public class MessageHandlerModule {
	private final AtomicInteger connectionIdCounter = new AtomicInteger();

	@IntoSet
	@Provides
	@Singleton
	ReactiveMessageHandler<RequestType, ClientRequest, Response, Object> editDocumentReactiveRequestHandler(
			RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub documentStorageService,
			PrometheusMeterRegistry metricRegistry
	) {
		return new CountingReactiveMessageHandler<>(
				new EditDocumentReactiveRequestHandler(() -> documentStorageService),
				Counter.builder("document.edit.count").register(metricRegistry)
		);
	}

	@IntoSet
	@Provides
	@Singleton
	ReactiveMessageHandler<RequestType, ClientRequest, Response, Object> connectRequestHandler(
			RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub documentStorageService,
			PrometheusMeterRegistry metricRegistry,
			Supplier<Integer> connectIdProvider
	) {
		var reactiveDocumentChangesPublisher = new ReactiveDocumentChangesPublisher(() -> documentStorageService);
		return new CountingReactiveMessageHandler<>(
				new ConnectReactiveRequestHandler(connectIdProvider, () -> documentStorageService, reactiveDocumentChangesPublisher),
				Counter.builder("document.connected.count").register(metricRegistry)
		);
	}

	@Provides
	@Singleton
	Supplier<Integer> connectIdProvider() {
		return connectionIdCounter::incrementAndGet;
	}
}
