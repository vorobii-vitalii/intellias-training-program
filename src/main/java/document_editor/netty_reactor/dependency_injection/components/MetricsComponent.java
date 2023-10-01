package document_editor.netty_reactor.dependency_injection.components;

import javax.inject.Singleton;

import dagger.Component;
import document_editor.netty_reactor.dependency_injection.modules.MetricsModule;
import io.micrometer.prometheus.PrometheusMeterRegistry;

@Component(modules = MetricsModule.class)
@Singleton
public interface MetricsComponent {
	PrometheusMeterRegistry getMeterRegistry();
}
