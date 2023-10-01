package document_editor.netty_reactor.dependency_injection.modules;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

@Module
public class MetricsModule {

	@Provides
	@Singleton
	PrometheusMeterRegistry meterRegistry() {
		return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
	}

}
